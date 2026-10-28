// Administrator approval for a faculty registration.
// Usage: node scripts/approveUser.js user@college.edu      (or edit users/{uid}.verified in the Firebase console)
const admin = require("firebase-admin");
admin.initializeApp({ credential: admin.credential.applicationDefault() });

const email = process.argv[2];
if (!email) { console.error("Usage: node scripts/approveUser.js <email>"); process.exit(1); }
(async () => {
  const user = await admin.auth().getUserByEmail(email);
  await admin.firestore().doc(`users/${user.uid}`).update({ verified: true });
  console.log(`Approved ${email}`);
})().catch((e) => { console.error(e); process.exit(1); });
