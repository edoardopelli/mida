import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-google-guide-page',
  imports: [RouterLink],
  templateUrl: './google-guide-page.html',
  styleUrl: './google-guide-page.scss',
})
export class GoogleGuidePage {
  readonly redirectUri = `${window.location.origin}/api/google/oauth/callback`;
}
