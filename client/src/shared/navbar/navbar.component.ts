import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../app/services/auth.service';
import { UserService } from '../../app/services/user.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './navbar.component.html'
})
export class NavbarComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private userService = inject(UserService);
  private router = inject(Router);
  private userSub!: Subscription;

  username: string = 'Curator';
  avatarUrl: string | null = null;
  isLoading: boolean = true;

  ngOnInit(): void {
    this.userSub = this.userService.user$.subscribe((user) => {
      if (user) {
        this.username = user.name || 'Curator';
        this.avatarUrl = user.avatarUrl || null;
      }
    });

    this.fetchUserProfile();
  }

  fetchUserProfile(): void {
    this.isLoading = true;
    this.authService.getProfile().subscribe({
      next: () => (this.isLoading = false),
      error: () => (this.isLoading = false)
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  
  ngOnDestroy(): void {
    if (this.userSub) {
      this.userSub.unsubscribe();
    }
  }
}