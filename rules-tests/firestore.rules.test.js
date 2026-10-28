// Run:  cd rules-tests && npm install && npm test   (needs the Firebase CLI + Java for the emulator)
const { test, before, after, beforeEach } = require("node:test");
const fs = require("fs");
const path = require("path");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { doc, getDoc, setDoc, updateDoc, collection, addDoc, serverTimestamp, writeBatch } = require("firebase/firestore");

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-facultyconnect",
    firestore: { rules: fs.readFileSync(path.join(__dirname, "../firebase/firestore.rules"), "utf8") },
  });
});
after(async () => env.cleanup());

const profile = (name, email) => ({
  fullName: name, email, department: "CE", designation: "Professor", subjects: ["OS"],
  photoUrl: "", teamsLink: "", verified: false, notifyMessages: true, notifyAssignments: true,
  createdAt: serverTimestamp(),
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [id, n] of [["a", "A"], ["b", "B"], ["c", "C"]]) {
      await setDoc(doc(db, "users", id), { ...profile(n, `${id}@x.edu`), verified: true, createdAt: new Date() });
    }
    await setDoc(doc(db, "conversations", "a_b"), {
      participants: ["a", "b"], participantNames: { a: "A", b: "B" }, participantPhotos: { a: "", b: "" },
      lastMessage: "", lastSenderId: "", unread: { a: 0, b: 0 }, createdAt: new Date(),
    });
    await setDoc(doc(db, "conversations", "a_b", "messages", "m1"), {
      senderId: "a", text: "hi", readBy: ["a"], createdAt: new Date(),
    });
  });
});

test("participant can read their conversation messages", async () => {
  const db = env.authenticatedContext("b", { email: "b@x.edu" }).firestore();
  await assertSucceeds(getDoc(doc(db, "conversations", "a_b", "messages", "m1")));
});

test("non-participant (Faculty C) cannot read A-B messages", async () => {
  const db = env.authenticatedContext("c", { email: "c@x.edu" }).firestore();
  await assertFails(getDoc(doc(db, "conversations", "a_b", "messages", "m1")));
  await assertFails(getDoc(doc(db, "conversations", "a_b")));
});

test("cannot send a message as someone else", async () => {
  const db = env.authenticatedContext("a", { email: "a@x.edu" }).firestore();
  await assertFails(setDoc(doc(db, "conversations", "a_b", "messages", "m2"), {
    senderId: "b", text: "spoof", readBy: ["b"], createdAt: serverTimestamp(),
  }));
});

test("unauthenticated user can read nothing", async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, "users", "a")));
});

test("user cannot mark themselves verified without matching domain", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users", "d"), { ...profile("D", "d@other.com"), createdAt: new Date() });
  });
  const db = env.authenticatedContext("d", { email: "d@other.com", email_verified: true }).firestore();
  await assertFails(updateDoc(doc(db, "users", "d"), { verified: true }));
});

test("user cannot create a profile that is already verified or has a role", async () => {
  const db = env.authenticatedContext("e", { email: "e@x.edu" }).firestore();
  await assertFails(setDoc(doc(db, "users", "e"), { ...profile("E", "e@x.edu"), verified: true }));
  await assertFails(setDoc(doc(db, "users", "e"), { ...profile("E", "e@x.edu"), role: "admin" }));
  await assertSucceeds(setDoc(doc(db, "users", "e"), profile("E", "e@x.edu")));
});

test("regular user cannot close someone else's post", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "lecturePosts", "p1"), {
      posterId: "a", posterName: "A", posterDepartment: "CE", posterPhotoUrl: "", type: "extra", subject: "OS",
      className: "TE", room: "101", reason: "x", startAt: new Date(Date.now() + 1e7), endAt: new Date(Date.now() + 2e7),
      status: "available", createdAt: new Date(), updatedAt: new Date(),
    });
  });
  const db = env.authenticatedContext("b", { email: "b@x.edu" }).firestore();
  await assertFails(updateDoc(doc(db, "lecturePosts", "p1"), { status: "closed", updatedAt: serverTimestamp() }));
});

test("assignment cannot be created without the matching post update (and non-owner cannot assign)", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "lecturePosts", "p2"), {
      posterId: "a", posterName: "A", posterDepartment: "CE", posterPhotoUrl: "", type: "extra", subject: "OS",
      className: "TE", room: "101", reason: "x", startAt: new Date(Date.now() + 1e7), endAt: new Date(Date.now() + 2e7),
      status: "available", createdAt: new Date(), updatedAt: new Date(),
    });
  });
  const dbB = env.authenticatedContext("b", { email: "b@x.edu" }).firestore();
  // B tries to assign himself
  const batch = writeBatch(dbB);
  batch.update(doc(dbB, "lecturePosts", "p2"), { status: "assigned", assignedToId: "b", assignedToName: "B", updatedAt: serverTimestamp() });
  await assertFails(batch.commit());
});
