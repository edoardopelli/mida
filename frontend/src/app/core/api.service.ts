import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ClassroomCourse,
  ClassroomFilters,
  ClassroomItem,
  ClassroomItemSummary,
  DiaryDateSummary,
  DiaryEntry,
  DiaryImportSummary,
  DiaryQueryParams,
  EmailFilters,
  EmailMessage,
  EmailSummary,
  GmailStatus,
  GoogleClientStatus,
  GoogleStatus,
  HomeworkFilters,
  HomeworkQueryParams,
  HomeworkView,
  ImportIssue,
  ImportIssueStatus,
  ImportStatus,
  ImportSummary,
  NotificationPreferences,
  PageResponse,
  PushSubscriptionDto,
  TestNotificationResponse,
  UpdateGoogleSettings,
  UpdateNotificationPreferences,
  VapidPublicKey,
} from './models';

@Injectable({ providedIn: 'root' })
export class MidaApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api';

  listHomework(params: HomeworkQueryParams): Observable<PageResponse<HomeworkView>> {
    return this.http.get<PageResponse<HomeworkView>>(`${this.baseUrl}/homework`, {
      params: this.homeworkParams(params),
    });
  }

  calendarHomework(params: HomeworkQueryParams & { from: string; to: string }): Observable<HomeworkView[]> {
    return this.http.get<HomeworkView[]>(`${this.baseUrl}/homework/calendar`, {
      params: this.homeworkParams(params),
    });
  }

  homework(id: string): Observable<HomeworkView> {
    return this.http.get<HomeworkView>(`${this.baseUrl}/homework/${encodeURIComponent(id)}`);
  }

  setCompletion(id: string, completed: boolean): Observable<HomeworkView> {
    return this.http.patch<HomeworkView>(
      `${this.baseUrl}/homework/${encodeURIComponent(id)}/completion`,
      { completed },
    );
  }

  subjects(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/homework/subjects`);
  }

  teachers(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/homework/teachers`);
  }

  imports(page = 0, size = 20): Observable<PageResponse<ImportSummary>> {
    return this.http.get<PageResponse<ImportSummary>>(`${this.baseUrl}/imports`, {
      params: new HttpParams().set('page', page.toString()).set('size', size.toString()),
    });
  }

  issues(status: ImportIssueStatus | 'ALL' = 'ALL', page = 0, size = 20): Observable<PageResponse<ImportIssue>> {
    return this.http.get<PageResponse<ImportIssue>>(`${this.baseUrl}/imports/issues`, {
      params: new HttpParams()
        .set('status', status)
        .set('page', page.toString())
        .set('size', size.toString()),
    });
  }

  importStatus(): Observable<ImportStatus> {
    return this.http.get<ImportStatus>(`${this.baseUrl}/imports/status`);
  }

  diaryEntries(params: DiaryQueryParams): Observable<PageResponse<DiaryEntry>> {
    return this.http.get<PageResponse<DiaryEntry>>(`${this.baseUrl}/diary`, {
      params: this.diaryParams(params),
    });
  }

  diaryDay(date: string, filters: { subject?: string; teacher?: string; q?: string } = {}): Observable<DiaryEntry[]> {
    let params = new HttpParams().set('date', date);
    if (filters.subject) {
      params = params.set('subject', filters.subject);
    }
    if (filters.teacher) {
      params = params.set('teacher', filters.teacher);
    }
    if (filters.q?.trim()) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<DiaryEntry[]>(`${this.baseUrl}/diary/day`, { params });
  }

  diaryDates(): Observable<DiaryDateSummary[]> {
    return this.http.get<DiaryDateSummary[]>(`${this.baseUrl}/diary/dates`);
  }

  diarySubjects(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/diary/subjects`);
  }

  diaryTeachers(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/diary/teachers`);
  }

  diaryImports(page = 0, size = 10): Observable<PageResponse<DiaryImportSummary>> {
    return this.http.get<PageResponse<DiaryImportSummary>>(`${this.baseUrl}/imports/diary`, {
      params: new HttpParams().set('page', page.toString()).set('size', size.toString()),
    });
  }

  notificationPreferences(): Observable<NotificationPreferences> {
    return this.http.get<NotificationPreferences>(`${this.baseUrl}/notifications/preferences`);
  }

  updateNotificationPreferences(preferences: UpdateNotificationPreferences): Observable<NotificationPreferences> {
    return this.http.put<NotificationPreferences>(
      `${this.baseUrl}/notifications/preferences`,
      preferences,
    );
  }

  vapidPublicKey(): Observable<VapidPublicKey> {
    return this.http.get<VapidPublicKey>(`${this.baseUrl}/notifications/public-key`);
  }

  subscribePush(subscription: { endpoint: string; p256dh: string; auth: string }): Observable<PushSubscriptionDto> {
    return this.http.post<PushSubscriptionDto>(`${this.baseUrl}/notifications/subscriptions`, subscription);
  }

  unsubscribePush(endpoint: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/notifications/subscriptions`, {
      body: { endpoint },
    });
  }

  sendTestNotification(): Observable<TestNotificationResponse> {
    return this.http.post<TestNotificationResponse>(`${this.baseUrl}/notifications/test`, {});
  }

  googleStatus(): Observable<GoogleStatus> {
    return this.http.get<GoogleStatus>(`${this.baseUrl}/google/status`);
  }

  googleClientStatus(): Observable<GoogleClientStatus> {
    return this.http.get<GoogleClientStatus>(`${this.baseUrl}/google/client`);
  }

  importGoogleClient(json: string): Observable<GoogleClientStatus> {
    return this.http.post<GoogleClientStatus>(`${this.baseUrl}/google/client`, { json });
  }

  deleteGoogleClient(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/google/client`);
  }

  gmailStatus(): Observable<GmailStatus> {
    return this.http.get<GmailStatus>(`${this.baseUrl}/google/gmail/status`);
  }

  syncGmail(): Observable<{ started: boolean; status: string }> {
    return this.http.post<{ started: boolean; status: string }>(
      `${this.baseUrl}/google/gmail/sync`,
      {},
    );
  }

  googleEmails(
    filters: EmailFilters,
    page = 0,
    size = 20,
  ): Observable<PageResponse<EmailSummary>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (filters.from.trim()) {
      params = params.set('from', filters.from.trim());
    }
    if (filters.q.trim()) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<PageResponse<EmailSummary>>(`${this.baseUrl}/mail/emails`, {
      params,
    });
  }

  googleEmail(id: string): Observable<EmailMessage> {
    return this.http.get<EmailMessage>(
      `${this.baseUrl}/mail/emails/${encodeURIComponent(id)}`,
    );
  }

  googleClassroom(
    filters: ClassroomFilters,
    page = 0,
    size = 20,
  ): Observable<PageResponse<ClassroomItemSummary>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (filters.courseId) {
      params = params.set('courseId', filters.courseId);
    }
    if (filters.type) {
      params = params.set('type', filters.type);
    }
    if (filters.q.trim()) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<PageResponse<ClassroomItemSummary>>(
      `${this.baseUrl}/google/classroom`,
      { params },
    );
  }

  googleClassroomItem(id: string): Observable<ClassroomItem> {
    return this.http.get<ClassroomItem>(
      `${this.baseUrl}/google/classroom/${encodeURIComponent(id)}`,
    );
  }

  googleClassroomCourses(): Observable<ClassroomCourse[]> {
    return this.http.get<ClassroomCourse[]>(`${this.baseUrl}/google/classroom/courses`);
  }

  googleSync(): Observable<{ started: boolean; status: string }> {
    return this.http.post<{ started: boolean; status: string }>(
      `${this.baseUrl}/google/sync`,
      {},
    );
  }

  googleDisconnect(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/google/disconnect`, {});
  }

  updateGoogleSettings(settings: UpdateGoogleSettings): Observable<GoogleStatus> {
    return this.http.put<GoogleStatus>(`${this.baseUrl}/google/settings`, settings);
  }

  attachmentUrl(id: string): string {
    return `${this.baseUrl}/attachments/${encodeURIComponent(id)}`;
  }

  private homeworkParams(params: HomeworkQueryParams): HttpParams {
    let httpParams = new HttpParams();
    if (params.from) {
      httpParams = httpParams.set('from', params.from);
    }
    if (params.to) {
      httpParams = httpParams.set('to', params.to);
    }
    httpParams = httpParams.set('dateField', params.dateField);
    if (params.subject) {
      httpParams = httpParams.set('subject', params.subject);
    }
    if (params.teacher) {
      httpParams = httpParams.set('teacher', params.teacher);
    }
    if (params.completion === 'open') {
      httpParams = httpParams.set('completed', 'false');
    } else if (params.completion === 'completed') {
      httpParams = httpParams.set('completed', 'true');
    }
    if (params.q?.trim()) {
      httpParams = httpParams.set('q', params.q.trim());
    }
    if (params.page != null) {
      httpParams = httpParams.set('page', params.page.toString());
    }
    if (params.size != null) {
      httpParams = httpParams.set('size', params.size.toString());
    }
    return httpParams;
  }

  private diaryParams(params: DiaryQueryParams): HttpParams {
    let httpParams = new HttpParams();
    if (params.from) {
      httpParams = httpParams.set('from', params.from);
    }
    if (params.to) {
      httpParams = httpParams.set('to', params.to);
    }
    if (params.subject) {
      httpParams = httpParams.set('subject', params.subject);
    }
    if (params.teacher) {
      httpParams = httpParams.set('teacher', params.teacher);
    }
    if (params.q?.trim()) {
      httpParams = httpParams.set('q', params.q.trim());
    }
    if (params.page != null) {
      httpParams = httpParams.set('page', params.page.toString());
    }
    if (params.size != null) {
      httpParams = httpParams.set('size', params.size.toString());
    }
    return httpParams;
  }
}

export type { HomeworkFilters };
