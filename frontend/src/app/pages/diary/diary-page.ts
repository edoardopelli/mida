import { Component, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DiaryStore } from '../../core/diary-store';
import { DiaryEntry } from '../../core/models';
import { formatDate, formatLongDate, todayIso } from '../../core/format';
import { subjectPalette } from '../../core/subject-colors';

@Component({
  selector: 'app-diary-page',
  imports: [RouterLink],
  templateUrl: './diary-page.html',
  styleUrl: './diary-page.scss',
})
export class DiaryPage implements OnInit, OnDestroy {
  readonly store = inject(DiaryStore);
  readonly formatDate = formatDate;
  readonly formatLongDate = formatLongDate;
  readonly today = todayIso();

  readonly selectedLabel = computed(() => {
    const date = this.store.selectedDate();
    return date ? formatLongDate(date) : 'Nessun giorno selezionato';
  });

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    void this.store.initialize();
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => this.store.setFilters({ q: value }), 320);
  }

  onSubject(event: Event): void {
    this.store.setFilters({ subject: (event.target as HTMLSelectElement).value });
  }

  onTeacher(event: Event): void {
    this.store.setFilters({ teacher: (event.target as HTMLSelectElement).value });
  }

  onDate(event: Event): void {
    this.store.selectDate((event.target as HTMLInputElement).value);
  }

  palette(entry: DiaryEntry) {
    return subjectPalette(entry.subject);
  }

  dateChipLabel(date: string): string {
    return formatDate(date);
  }
}
