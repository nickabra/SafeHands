const { onCall, HttpsError } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');
const { onSchedule } = require('firebase-functions/v2/scheduler');

// Inizializzazione ibrida
if (!admin.apps.length) {
  try {
    // inizializzazione automatica (per scheduled)
    admin.initializeApp();
    console.log('Admin SDK inizializzato senza service account manuale');
  } catch (error) {
    // fallito, quindi usa il service account manuale (per onCall)
    console.log('Fallback al service account manuale');
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
  }
}

const db = admin.firestore();
const auth = admin.auth();

// Funzione di validazione email
function isValidEmail(email) {
  if (!email || typeof email !== 'string') return false;
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email) && email.length <= 254;
}

function generateNotificationPayload(parentUid, eventType, details, userDoc) {
  if (!userDoc.exists) {
    throw new HttpsError(
      'not-found',
      'Documento utente non trovato in Firestore'
    );
  }

  const fcmToken = userDoc.data().fcmToken;
  if (!fcmToken) {
    throw new HttpsError('failed-precondition', 'Token FCM non trovato');
  }

  let title = '';
  let body = '';

  if (eventType === 'task_completed') {
    title = 'Task completato';
    body = `Il task "${details.taskName}" è stato completato dal caregiver.`;
  } else if (eventType === 'parameter_sent') {
    title = 'Nuova misurazione';
    body =
      `Nuova misurazione: ${details.parameter || details.parameterName}\n` +
      Object.entries(details || {})
        .filter(([key]) => key !== 'parameter' && key !== 'parameterName')
        .map(([k, v]) => `${k}: ${v}`)
        .join('\n');
  } else {
    throw new HttpsError('invalid-argument', 'Tipo evento non valido');
  }

  return {
    token: fcmToken,
    data: {
      parentUid,
      eventType,
      ...details,
    },
  };
}

// Funzione per ottenere UID a partire dall'email del parent
exports.getUidFromEmail = onCall(
  { region: 'europe-west1' },
  async (request) => {
    console.log('Data ricevuta:', request.data);

    const parentEmail = request.data?.parentEmail;
    if (!parentEmail || !isValidEmail(parentEmail)) {
      console.error('Errore: email del parent non valida');
      throw new HttpsError('invalid-argument', 'Email del parent non valida');
    }

    try {
      const userRecord = await admin.auth().getUserByEmail(parentEmail);
      console.log('User found:', userRecord.uid);
      return { uid: userRecord.uid };
    } catch (error) {
      console.error('Errore getUidFromEmail:', error);
      throw new HttpsError('not-found', error.message);
    }
  }
);

// Funzione per aggiornare lo stato di un task da parte del caregiver
exports.updateTaskStatus = onCall(
  { region: 'europe-west1' },
  async (request) => {
    try {
      const { parentUid, caregiverId, taskId, status } = request.data;

      if (!parentUid || !caregiverId || !taskId || status === undefined) {
        throw new HttpsError(
          'invalid-argument',
          'Manca parentUid, caregiverId, taskId o status'
        );
      }

      // Autorizzazione caregiver
      const caregiverDocRef = db
        .collection('caregivers')
        .doc(`${parentUid}_${caregiverId}`);
      const caregiverDoc = await caregiverDocRef.get();
      if (!caregiverDoc.exists) {
        throw new HttpsError('permission-denied', 'Caregiver non autorizzato');
      }

      // Aggiorna lo stato del task
      const taskRef = db
        .collection('todoLists')
        .doc(parentUid)
        .collection('tasks')
        .doc(taskId);
      await taskRef.update({ status });

      const taskDoc = await taskRef.get();
      if (!taskDoc.exists) {
        throw new HttpsError('not-found', 'Task non trovato');
      }
      const taskTitle = taskDoc.data().title;

      // Se completato, invia notifica
      if (status) {
        const userRecord = await auth.getUser(parentUid);
        const parentEmailId = userRecord.email.replace(/\./g, '_');
        if (!parentEmailId) {
          throw new HttpsError('not-found', 'Email del parent non trovata');
        }
        const userDoc = await db.collection('users').doc(parentEmailId).get();

        const message = generateNotificationPayload(
          parentUid,
          'task_completed',
          { taskName: taskTitle },
          userDoc
        );

        console.log('Messaggio TASK da inviare:', message);

        const response = await admin.messaging().send(message);
        console.log('Messaggio TASK inviato con successo:', response);
      }

      return { success: true, taskId, status };
    } catch (error) {
      console.error('Errore updateTaskStatus:', error);
      throw new HttpsError('internal', error.message);
    }
  }
);

exports.saveParameter = onCall({ region: 'europe-west1' }, async (request) => {
  try {
    const { parentUid, data } = request.data;

    if (!parentUid || !data) {
      throw new HttpsError('invalid-argument', 'Manca parentUid o data');
    }

    const now = new Date();
    const date = now.toISOString().split('T')[0]; // yyyy-MM-dd
    const time = now.toTimeString().split(' ')[0]; // HH:mm:ss

    const parameterData = { ...data, time };
    const updateData = { [time]: parameterData };

    // Salva nel DB
    await db
      .collection('parameters')
      .doc(parentUid)
      .collection('recived')
      .doc(date)
      .set(updateData, { merge: true });

    console.log('Parametro salvato per:', parentUid, parameterData);
    // Invia notifica
    const userRecord = await auth.getUser(parentUid);
    const parentEmailId = userRecord.email.replace(/\./g, '_');
    if (!parentEmailId) {
      throw new HttpsError('not-found', 'Email del parent non trovata');
    }
    const userDoc = await db.collection('users').doc(parentEmailId).get();

    const message = generateNotificationPayload(
      parentUid,
      'parameter_sent',
      { parameter: data.parameter, ...data, time },
      userDoc
    );

    console.log('Messaggio PARAMETER da inviare:', message);

    const response = await admin.messaging().send(message);
    console.log('Messaggio PARAMETER inviato con successo:', response);

    return { success: true, saved: parameterData };
  } catch (error) {
    console.error('Errore nel salvataggio parametro:', error);
    throw new HttpsError('internal', error.message);
  }
});

exports.saveParameterNotification = onCall(
  { region: 'europe-west1' },
  async (request) => {
    // Controllo autenticazione
    if (!request.auth) {
      throw new HttpsError('unauthenticated', 'Utente non autenticato');
    }

    const data = request.data;
    const { parentEmail, parameter, title, body, time, details } = data;

    if (!parentEmail || parentEmail.trim() === '') {
      throw new HttpsError('invalid-argument', 'Email mancante');
    }

    if (!parameter || parameter.trim() === '') {
      throw new HttpsError('invalid-argument', 'Parameter mancante');
    }

    if (!title || title.trim() === '') {
      throw new HttpsError('invalid-argument', 'Titolo mancante');
    }

    if (!body || body.trim() === '') {
      throw new HttpsError('invalid-argument', 'Body mancante');
    }

    await db
      .collection('users')
      .doc(parentEmail)
      .collection('parameters_notify_log')
      .add({
        title,
        body,
        parameter,
        time,
        details,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });

    console.log('Notifica di parametro salvata per:', parentEmail);

    return { success: true };
  }
);

exports.resetTasksDaily = onSchedule(
  {
    schedule: '0 0 * * *',
    timeZone: 'Europe/Rome',
    timeoutSeconds: 540,
    memory: '1GB',
    region: 'europe-west1',
    maxInstances: 10,
    serviceAccount:
      'firebase-adminsdk-fbsvc@safehands-da1fb.iam.gserviceaccount.com',
  },
  async (event) => {
    const MAX_BATCH_SIZE = 490;

    try {
      console.log('Inizio reset tasks giornaliero');

      let lastTodoListDoc = null;
      let hasMoreTodoLists = true;

      while (hasMoreTodoLists) {
        let todoListsQuery = db.collection('todoLists').limit(5);
        if (lastTodoListDoc)
          todoListsQuery = todoListsQuery.startAfter(lastTodoListDoc);

        const todoListsSnapshot = await todoListsQuery.get();

        if (todoListsSnapshot.empty) {
          hasMoreTodoLists = false;
          break;
        }

        for (const parentDoc of todoListsSnapshot.docs) {
          console.log(`Elaborando todoList: ${parentDoc.id}`);

          let lastTaskDoc = null;
          let hasMoreTasks = true;

          while (hasMoreTasks) {
            let tasksQuery = parentDoc.ref
              .collection('tasks')
              .where('status', '==', true)
              .limit(MAX_BATCH_SIZE);

            if (lastTaskDoc) tasksQuery = tasksQuery.startAfter(lastTaskDoc);

            const tasksSnapshot = await tasksQuery.get();

            if (tasksSnapshot.empty) {
              hasMoreTasks = false;
              break;
            }

            if (tasksSnapshot.size > 0) {
              const batch = db.batch();
              tasksSnapshot.forEach((taskDoc) => {
                batch.update(taskDoc.ref, { status: false });
              });

              await batch.commit();
              console.log(
                `Reset ${tasksSnapshot.size} tasks in ${parentDoc.id}`
              );
            }

            lastTaskDoc = tasksSnapshot.docs[tasksSnapshot.docs.length - 1];
            hasMoreTasks = tasksSnapshot.docs.length === MAX_BATCH_SIZE;
          }
        }

        lastTodoListDoc =
          todoListsSnapshot.docs[todoListsSnapshot.docs.length - 1];
        hasMoreTodoLists = todoListsSnapshot.docs.length === 5;
      }

      console.log('Reset tasks completato con successo');
      return null;
    } catch (error) {
      console.error('ERRORE CRITICO:', error);
      console.error('Stack trace:', error.stack);
      return null;
    }
  }
);

exports.cleanParametersNotifyLogDaily = onSchedule(
  {
    schedule: '0 0 * * *',
    timeZone: 'Europe/Rome',
    timeoutSeconds: 540,
    memory: '1GB',
    region: 'europe-west1',
    maxInstances: 10,
    serviceAccount:
      'firebase-adminsdk-fbsvc@safehands-da1fb.iam.gserviceaccount.com',
  },
  async (event) => {
    const MAX_BATCH_SIZE = 490; // Limite sicuro per Firestore batch

    try {
      console.log('Inizio pulizia parameters_notify_log');

      // Ottieni tutti gli utenti con paginazione
      let lastUserDoc = null;
      let hasMoreUsers = true;
      let totalDeleted = 0;
      let processedUsers = 0;

      while (hasMoreUsers) {
        let usersQuery = db.collection('users').limit(10); // 10 utenti per ciclo
        if (lastUserDoc) usersQuery = usersQuery.startAfter(lastUserDoc);

        const usersSnapshot = await usersQuery.get();

        if (usersSnapshot.empty) {
          hasMoreUsers = false;
          break;
        }

        processedUsers += usersSnapshot.size;
        console.log(
          `Elaborando batch di ${usersSnapshot.size} utenti. Totali processati: ${processedUsers}`
        );

        for (const userDoc of usersSnapshot.docs) {
          const userId = userDoc.id;

          try {
            console.log(`Pulizia log per utente: ${userId}`);

            const logsRef = db
              .collection('users')
              .doc(userId)
              .collection('parameters_notify_log');
            let lastLogDoc = null;
            let hasMoreLogs = true;
            let userDeleted = 0;

            while (hasMoreLogs) {
              let logsQuery = logsRef.limit(MAX_BATCH_SIZE);
              if (lastLogDoc) logsQuery = logsQuery.startAfter(lastLogDoc);

              const logsSnapshot = await logsQuery.get();

              if (logsSnapshot.empty) {
                hasMoreLogs = false;
                break;
              }

              if (logsSnapshot.size > 0) {
                const batch = db.batch();
                logsSnapshot.forEach((logDoc) => {
                  batch.delete(logDoc.ref);
                });

                await batch.commit();
                userDeleted += logsSnapshot.size;
                totalDeleted += logsSnapshot.size;

                console.log(
                  `Eliminati batch di ${logsSnapshot.size} log per ${userId} (totali utente: ${userDeleted})`
                );
              }

              lastLogDoc = logsSnapshot.docs[logsSnapshot.docs.length - 1];
              hasMoreLogs = logsSnapshot.docs.length === MAX_BATCH_SIZE;

              // Piccola pausa per evitare overload
              if (hasMoreLogs) {
                await new Promise((resolve) => setTimeout(resolve, 100));
              }
            }

            if (userDeleted > 0) {
              console.log(
                `Riepilogo utente ${userId}: eliminati ${userDeleted} log`
              );
            } else {
              console.log(`Nessun log da eliminare per ${userId}`);
            }
          } catch (userError) {
            console.error(
              `Errore nell'elaborazione utente ${userId}:`,
              userError
            );
            // Continua con gli altri utenti invece di bloccare tutto
          }
        }

        lastUserDoc = usersSnapshot.docs[usersSnapshot.docs.length - 1];
        hasMoreUsers = usersSnapshot.docs.length === 10;

        // Pausa tra batch di utenti
        if (hasMoreUsers) {
          await new Promise((resolve) => setTimeout(resolve, 500));
        }
      }

      console.log(
        `Pulizia completata. Totale utenti processati: ${processedUsers}, Totale log eliminati: ${totalDeleted}`
      );
      return null;
    } catch (error) {
      console.error(
        'ERRORE CRITICO nella pulizia parameters_notify_log:',
        error
      );
      console.error('Stack trace:', error.stack);
      return null;
    }
  }
);
