import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../shared/navbar/navbar.component';
import { FooterComponent } from '../shared/footer/footer.component';
import { AuthService } from './services/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
   standalone: true,
  imports: [RouterOutlet, NavbarComponent, FooterComponent ,CommonModule],
  templateUrl: './app.component.html',
})
export class AppComponent {
  title = 'frontend';
  constructor(public authService: AuthService) {}
}
