// Sends an FCM push whenever a notification document is created or refreshed.
// Requires the Firebase Blaze (pay-as-you-go) plan to deploy. The app works without it (in-app only, no push).
// Message TEXT is deliberately NOT sent through FCM; only ids and a generic title travel in the payload.
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

exports.pushOnNotification = onDocumentWritten("notifications/{id}", async (event) => {
  const after = event.data && event.data.after.exists ? event.data.after.data() : null;
  if (!after || after.read === true) return;

  // Ignore updates that only flip `read` (createdAt unchanged).
  const before = event.data.before.exists ? event.data.before.data() : null;
  if (before && before.createdAt && after.createdAt && before.createdAt.isEqual(after.createdAt)) return;

  const db = admin.firestore();
  const userSnap = await db.doc(`users/${after.toUid}`).get();
  if (!userSnap.exists) return;
  const prefs = userSnap.data();
  if (after.type === "message" && prefs.notifyMessages === false) return;
  if (after.type !== "message" && prefs.notifyAssignments === false) return;

  const devices = await db.collection(`users/${after.toUid}/devices`).get();
  const tokens = devices.docs.map((d) => d.id);
  if (tokens.length === 0) return;

  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    data: {
      type: String(after.type || ""),
      refType: String(after.refType || ""),
      refId: String(after.refId || ""),
      fromUid: String(after.fromUid || ""),
      title: String(after.title || "FacultyConnect"),
      body: after.type === "message" ? "" : String(after.body || ""),
    },
    android: { priority: "high" },
  });

  // Remove tokens that are no longer valid.
  const stale = [];
  res.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      stale.push(tokens[i]);
    }
  });
  await Promise.all(stale.map((t) => db.doc(`users/${after.toUid}/devices/${t}`).delete()));
});
