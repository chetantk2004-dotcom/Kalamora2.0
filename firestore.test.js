const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_writer_01";
const BOB_UID = "bob_writer_02";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function validProfilePayload(uid, ts = pastDate()) {
  return {
    userId: uid,
    displayName: "Aarav Sharma",
    penName: "Aarav Kalam",
    bio: "Poet and novelist exploring Urdu and Hindi verses.",
    email: "aarav@example.com",
    preferredLanguage: "Hindi",
    publicProfileEnabled: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validShayariPayload(uid, ts = pastDate()) {
  return {
    userId: uid,
    title: "ख़ामोशी के अल्फ़ाज़",
    content: "वक़्त की रेत पर कुछ ख़्वाब लिख दिए,\nहमने ख़ामोशी में भी सौ जवाब लिख दिए।",
    category: "Life",
    language: "Hindi",
    privacyStatus: "PRIVATE",
    publishStatus: "DRAFT",
    favorite: false,
    wordCount: 15,
    charCount: 82,
    authorName: "Aarav Kalam",
    cardTheme: "PARCHMENT",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validBookPayload(uid, ts = pastDate()) {
  return {
    userId: uid,
    title: "काग़ज़ के परिंदे",
    authorName: "Aarav Kalam",
    description: "एक संवेदनशील उपन्यास जो सपनों और यादों को जोड़ता है।",
    coverStyle: "MAHOGANY",
    language: "Hindi",
    genre: "Fiction",
    privacyStatus: "PRIVATE",
    publishStatus: "DRAFT",
    favorite: false,
    chapterCount: 0,
    totalWordCount: 0,
    totalCharCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validChapterPayload(uid, bookId, orderIndex = 0, ts = pastDate()) {
  return {
    userId: uid,
    bookId: bookId,
    title: "अध्याय 1: पहली बारिश",
    content: "# पहली बारिश\n\nआसमान में **गहरे बादल** छाए हुए थे।",
    orderIndex: orderIndex,
    textAlignment: "START",
    wordCount: 9,
    charCount: 54,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// 1. Unauthenticated access denial across all collections
test("Unauthenticated users are denied read and write on all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("shayari").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("books").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("books").doc("book_1").collection("chapters").get()
  );
});

// 2. User profile CRUD, PII isolation, and shadow update rejection
test("User profile: owner create/update/read succeeds; cross-user read and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Partial update of mutable fields succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      penName: "Kalamora Kavi",
      bio: "Updated author bio.",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user PII read and list must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: pastDate(500),
    })
  );

  // Immutable createdAt mutation must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

// 3. Shayari CRUD, Hindi Unicode, partial update, favorite toggle, and isolation
test("Shayari: owner create/list/update/delete succeeds; cross-user and invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const shayariRef = aliceDb.collection("users").doc(ALICE_UID).collection("shayari").doc("shayari_01");

  await assertSucceeds(shayariRef.set(validShayariPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("shayari").get());

  // Edit Shayari content & metadata
  await assertSucceeds(
    shayariRef.update({
      title: "नई ग़ज़ल",
      content: "दिल के आईने में आज फिर एक नया गीत है।",
      category: "Love",
      publishStatus: "PUBLISHED",
      wordCount: 9,
      charCount: 41,
      updatedAt: pastDate(1000),
    })
  );

  // Favorite toggle action
  await assertSucceeds(
    shayariRef.update({
      favorite: true,
      updatedAt: pastDate(500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("shayari").doc("shayari_future").set(
      validShayariPayload(ALICE_UID, new Date(Date.now() + 120000))
    )
  );

  // Reject @DocumentId "id" key inside body
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("shayari").doc("shayari_bad_id").set({
      ...validShayariPayload(ALICE_UID),
      id: "shayari_bad_id",
    })
  );

  // Reject cross-user read, update, delete
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("shayari").doc("shayari_01").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("shayari").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("shayari").doc("shayari_01").update({
      title: "Hijacked",
      updatedAt: pastDate(500),
    })
  );

  await assertSucceeds(shayariRef.delete());
});

// 4. Books and independent Chapters: parent gate, reorder, stats update, isolation
test("Books & Chapters: parent gate, independent chapters, reorder, stats update, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const bookRef = aliceDb.collection("users").doc(ALICE_UID).collection("books").doc("book_01");
  const ch1Ref = bookRef.collection("chapters").doc("ch_01");
  const ch2Ref = bookRef.collection("chapters").doc("ch_02");

  // Orphan chapter create before parent book exists MUST fail
  await assertFails(ch1Ref.set(validChapterPayload(ALICE_UID, "book_01", 0)));

  // Create parent book
  await assertSucceeds(bookRef.set(validBookPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("books").get());

  // Create two independent chapters
  await assertSucceeds(ch1Ref.set(validChapterPayload(ALICE_UID, "book_01", 0)));
  await assertSucceeds(
    ch2Ref.set({
      ...validChapterPayload(ALICE_UID, "book_01", 1),
      title: "Chapter 2: The Quiet Dusk",
      content: "Evenings carried the scent of monsoon earth.",
      wordCount: 7,
      charCount: 44,
    })
  );

  // List chapters in order
  await assertSucceeds(bookRef.collection("chapters").orderBy("orderIndex").get());

  // Update chapter 1 content without affecting chapter 2
  await assertSucceeds(
    ch1Ref.update({
      content: "Updated chapter 1 prose with **bold** and *italic*.",
      textAlignment: "JUSTIFY",
      wordCount: 8,
      charCount: 51,
      updatedAt: pastDate(1000),
    })
  );

  // Reorder chapter 2
  await assertSucceeds(
    ch2Ref.update({
      orderIndex: 0,
      updatedAt: pastDate(800),
    })
  );

  // Update denormalized book stats
  await assertSucceeds(
    bookRef.update({
      chapterCount: 2,
      totalWordCount: 15,
      totalCharCount: 95,
      updatedAt: pastDate(700),
    })
  );

  // Attempt to mutate immutable bookId on chapter MUST fail
  await assertFails(
    ch1Ref.update({
      bookId: "book_99",
      updatedAt: pastDate(500),
    })
  );

  // Cross-user access to book and chapters MUST fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("books").doc("book_01").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("books").doc("book_01").collection("chapters").get()
  );

  // Owner delete
  await assertSucceeds(ch1Ref.delete());
  await assertSucceeds(ch2Ref.delete());
  await assertSucceeds(bookRef.delete());
});
