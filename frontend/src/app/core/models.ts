export type DateField = 'DUE' | 'ASSIGNED';
export type CompletionFilter = 'all' | 'open' | 'completed';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface HomeworkView {
  id: string;
  importId: string;
  sourceCode: string;
  originalFilename: string;
  importedAt: string;
  assignedDate: string;
  assignedDateRaw: string;
  dueDate: string;
  dueDateRaw: string;
  subject: string;
  teacher: string;
  description: string;
  completed: boolean;
  completedAt: string | null;
  revision: number;
  updatedAt: string;
}

export interface HomeworkFilters {
  dateField: DateField;
  subject: string;
  teacher: string;
  completion: CompletionFilter;
  q: string;
}

export interface HomeworkQueryParams extends HomeworkFilters {
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface ImportSummary {
  id: string;
  sourceCode: string;
  originalFilename: string;
  filenameSuffix: number | null;
  sha256: string;
  importedAt: string;
  assignmentCount: number;
  createdCount: number;
  updatedCount: number;
  unchangedCount: number;
  schoolYearStart: number;
}

export type ImportIssueType =
  | 'PARSE_ERROR'
  | 'VALIDATION_ERROR'
  | 'READ_ERROR'
  | 'FILE_TOO_LARGE'
  | 'DUPLICATE_CONFLICT'
  | 'FILE_DELETION_FAILED'
  | 'FILE_CHANGED_DURING_IMPORT'
  | 'DIRECTORY_UNAVAILABLE'
  | 'PERSISTENCE_ERROR'
  | 'UNEXPECTED_ERROR';

export type ImportIssueStatus = 'OPEN' | 'RESOLVED';

export interface ImportIssue {
  id: string;
  type: ImportIssueType;
  status: ImportIssueStatus;
  filename: string | null;
  sourceCode: string | null;
  sha256: string | null;
  description: string;
  firstSeenAt: string;
  lastSeenAt: string;
  attempts: number;
  resolvedAt: string | null;
}

export interface ImportStatus {
  monitoringEnabled: boolean;
  state: 'STARTING' | 'ACTIVE' | 'ERROR' | 'DISABLED';
  lastScanAt: string | null;
  lastSuccessfulImportAt: string | null;
  totalImports: number;
  totalAssignments: number;
  totalDiaryImports: number;
  totalDiaryEntries: number;
  openIssues: number;
  activePushSubscriptions: number;
  directoryReadable: boolean;
  lastError: string | null;
  generatedAt: string;
}

export interface DiaryEntry {
  id: string;
  importId: string;
  sourceCode: string;
  originalFilename: string;
  importedAt: string;
  activityDate: string;
  activityDateRaw: string;
  period: number;
  periodLabel: string;
  subject: string;
  teacher: string;
  description: string;
  revision: number;
  updatedAt: string;
}

export interface DiaryDateSummary {
  date: string;
  entryCount: number;
  subjects: string[];
}

export interface DiaryImportSummary {
  id: string;
  sourceCode: string;
  originalFilename: string;
  filenameSuffix: number | null;
  sha256: string;
  importedAt: string;
  entryCount: number;
  createdCount: number;
  updatedCount: number;
  unchangedCount: number;
  schoolYearStart: number;
}

export interface DiaryFilters {
  subject: string;
  teacher: string;
  q: string;
}

export interface DiaryQueryParams extends DiaryFilters {
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface NotificationPreferences {
  enabled: boolean;
  newAssignmentsEnabled: boolean;
  remindersEnabled: boolean;
  reminderDaysBefore: number;
  reminderTime: string;
  timezone: string;
  updatedAt: string;
}

export interface UpdateNotificationPreferences {
  enabled: boolean;
  newAssignmentsEnabled: boolean;
  remindersEnabled: boolean;
  reminderDaysBefore: number;
  reminderTime: string;
  timezone: string;
}

export interface VapidPublicKey {
  publicKey: string;
  enabled: boolean;
  subject: string;
}

export interface PushSubscriptionDto {
  id: string;
  endpoint: string;
  active: boolean;
}

export interface TestNotificationResponse {
  sent: number;
  failed: number;
  expired: number;
  subscriptions: number;
}

export interface ImportCompletedPayload {
  kind?: 'HOMEWORK';
  importId: string;
  sourceCode: string;
  filename: string;
  assignmentCount: number;
  createdCount: number;
  updatedCount: number;
  unchangedCount: number;
  subjects: string[];
  subjectCounts: Record<string, number>;
  earliestDueDate?: string;
  fileDeleted: boolean;
}

export interface DiaryImportedPayload {
  importId: string;
  sourceCode: string;
  filename: string;
  entryCount: number;
  createdCount: number;
  updatedCount: number;
  unchangedCount: number;
  subjects: string[];
  activityDates: string[];
}

export interface HomeworkUpdatedPayload {
  assignmentId: string;
  completed: boolean;
  subject: string;
  dueDate: string;
}

export interface MidaEvent<T = unknown> {
  type: string;
  at: string;
  data: T;
}

export interface DashboardStats {
  total: number;
  open: number;
  completed: number;
  overdue: number;
  today: number;
  tomorrow: number;
  nextSevenDays: number;
  upcoming: HomeworkView[];
  recentImports: ImportSummary[];
  openIssues: ImportIssue[];
}

export type GoogleSyncStatus =
  | 'NEVER'
  | 'SYNCING'
  | 'OK'
  | 'ERROR'
  | 'REAUTH_REQUIRED';

export interface GoogleClientStatus {
  configured: boolean;
  clientId: string | null;
  projectId: string | null;
  redirectUri: string | null;
  importedAt: string | null;
}

export interface GoogleStatus {
  configured: boolean;
  connected: boolean;
  accountEmail: string | null;
  connectedAt: string | null;
  scopes: string[];
  lastSyncAt: string | null;
  lastSyncStatus: GoogleSyncStatus;
  lastError: string | null;
  syncInProgress: boolean;
  classroomEnabled: boolean;
  lastClassroomItemCount: number;
  lastNewClassroomItemCount: number;
  settingsUpdatedAt: string | null;
}

export interface GmailStatus {
  configured: boolean;
  connected: boolean;
  gmailEnabled: boolean;
  gmailQuery: string;
  lastSyncAt: string | null;
  lastSyncStatus: GoogleSyncStatus;
  lastError: string | null;
  syncInProgress: boolean;
  lastFetchedCount: number;
  lastNewCount: number;
  lastAttachmentCount: number;
  lastNewAttachmentCount: number;
}

export interface UpdateGoogleSettings {
  gmailQuery?: string;
  gmailEnabled?: boolean;
  classroomEnabled?: boolean;
}

export interface EmailSummary {
  id: string;
  from: string;
  subject: string;
  snippet: string;
  receivedAt: string;
  hasAttachments: boolean;
  attachmentCount: number;
}

export interface EmailAttachment {
  id: string;
  filename: string;
  mimeType: string;
  size: number;
  inline: boolean;
  importStatus: string | null;
  importMessage: string | null;
  downloadUrl: string | null;
}

export interface EmailMessage {
  id: string;
  from: string;
  to: string;
  cc: string;
  subject: string;
  bodyText: string;
  receivedAt: string;
  labels: string[];
  attachments: EmailAttachment[];
}

export interface EmailFilters {
  from: string;
  q: string;
}

export interface ClassroomCourse {
  courseId: string;
  courseName: string;
  courseSection: string | null;
}

export interface ClassroomItemSummary {
  id: string;
  courseId: string;
  courseName: string;
  courseSection: string | null;
  itemType: 'COURSEWORK' | 'MATERIAL' | 'ANNOUNCEMENT' | string;
  title: string;
  description: string;
  dueDate: string | null;
  dueTime: string | null;
  updateTime: string | null;
  materialCount: number;
  alternateLink: string | null;
}

export interface ClassroomMaterial {
  id: string;
  type: string;
  title: string;
  url: string | null;
  filename: string | null;
  mimeType: string | null;
  size: number;
  status: string;
  error: string | null;
  downloadUrl: string | null;
}

export interface ClassroomItem {
  id: string;
  courseId: string;
  courseName: string;
  courseSection: string | null;
  itemType: string;
  title: string;
  description: string;
  state: string;
  alternateLink: string | null;
  dueDate: string | null;
  dueTime: string | null;
  creationTime: string | null;
  updateTime: string | null;
  materials: ClassroomMaterial[];
}

export interface ClassroomFilters {
  courseId: string;
  type: string;
  q: string;
}

export interface GoogleSyncPayload {
  classroomItems: number;
  newClassroomItems: number;
  classroomMaterials: number;
  newClassroomMaterials: number;
  errors: number;
}

export interface MailSyncPayload {
  fetched: number;
  newEmails: number;
  attachments: number;
  newAttachments: number;
  errors: number;
}
