package com.subrosa.app.domain.model

/**
 * The steps of the app. Sessions are organized under clients and cases: HOME lists clients,
 * CLIENT_PROFILE lists a client's cases, NEW_CASE picks a matter for a new case, CASE_PROFILE shows
 * a case's documents + consultations, and the consultation flow (CONSENT…RESULTS) records a living,
 * resumable session for that case.
 */
enum class SessionPhase { HOME, CLIENT_PROFILE, CASE_PROFILE, NEW_CLIENT, NEW_CASE, CONSENT, CAPTURE, PROCESSING, RESULTS }

/** A session is in progress (resumable) until the lawyer seals it. */
enum class SessionStatus { IN_PROGRESS, SEALED }

/**
 * How speakers are attributed during capture. AUTO_ASSIST labels turns automatically (diarization on
 * the real path; the fixture's own labels on the scripted path) with every line tap-to-correctable;
 * MANUAL stamps each turn with the speaker the lawyer has selected on the plate.
 */
enum class CaptureMode { AUTO_ASSIST, MANUAL }
