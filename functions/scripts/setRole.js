// Run on an ADMIN machine only. Usage:  node scripts/setRole.js user@college.edu coordinator
// Needs a service-account key via GOOGLE_APPLICATION_CREDENTIALS. Never put that key in the app or in git.
const admin = require("firebase-admin");
admin.initializeApp({ credential: admin.credential.applicationDefault() });

const [email, role] = process.argv.slice(2);
if (!email || !["faculty", "coordinator", "admin"].includes(role)) {
  console.error("Usage: node scripts/setRole.js <email> <faculty|coordinator|admin>");
  process.exit(1);
}
(async () => {
  const user = await admin.auth().getUserByEmail(email);
  await admin.auth().setCustomUserClaims(user.uid, { role });
  console.log(`Set role '${role}' for ${email}. The user must sign out and in again.`);
})().catch((e) => { console.error(e); process.exit(1); });
