import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
interface ErrorDetails {
  title: string;
  subtitle: string;
  description: string;
  icon: string;
}

@Component({
  selector: 'app-error',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="min-h-screen bg-[#0a1120] flex items-center justify-center p-6 antialiased font-sans">
      <div class="w-full max-w-xl text-center">
        
        <!-- Big Status Code Graphic Background Text -->
        <h1 class="text-[9rem] sm:text-[13rem] font-black text-white/5 font-mono select-none leading-none tracking-tighter">
          {{ statusCode }}
        </h1>

        <!-- Error Visual Detail Content -->
        <div class="mt-[-4rem] sm:mt-[-6rem]">
          <div class="inline-flex items-center justify-center w-20 h-20 rounded-2xl bg-white/5 border border-white/10 text-3xl mb-6 shadow-xl">
            {{ config.icon }}
          </div>
          
          <h2 class="text-3xl font-extrabold text-white tracking-tight uppercase sm:text-4xl">
            {{ config.title }}
          </h2>
          
          <p class="mt-2 text-sm font-mono tracking-widest text-gray-400 uppercase">
            {{ config.subtitle }}
          </p>

          <p class="mt-6 text-base text-gray-400/80 max-w-md mx-auto leading-relaxed">
            {{ config.description }}
          </p>
        </div>

        <!-- Action Backlinks -->
        <div class="mt-12 flex flex-col sm:flex-row gap-4 justify-center items-center">
          <button (click)="goBack()" 
            class="w-full sm:w-auto border border-white/20 hover:border-white text-white font-bold text-xs uppercase tracking-widest py-3.5 px-6 rounded-lg transition-all bg-white/5">
            ← Return
          </button>
      
        </div>

        </div>
        </div>
        `
      })
      export class ErrorComponent implements OnInit {
        private route = inject(ActivatedRoute);
        private routery = inject(Router);
        private authService = inject(AuthService);

  statusCode = '404';
  config!: ErrorDetails;

  private errorSpecs: Record<string, ErrorDetails> = {
    '401': {
      title: 'Session Expired',
      subtitle: '401 - Unauthorized Access',
      description: 'Your security token is missing or has expired. Please clear your session locks and log back into your user portal profile.',
      icon: '🔒'
    },
    '403': {
      title: 'Access Denied',
      subtitle: '403 - Forbidden Area',
      description: 'Your security privilege tier is insufficient to view this application route. Contact your systems admin if this is an error.',
      icon: '🚫'
    },
    '404': {
      title: 'Page Not Found',
      subtitle: '404 - Absolute Resource Ghost',
      description: "The layout link you are targeting doesn't exist, has been dropped out of cache indices, or moved to alternative services.",
      icon: '🛸'
    },
    '500': {
      title: 'Server Melted Down',
      subtitle: '500 - Internal System Fault',
      description: 'The upstream gateway or target microservice broke connection loops or dropped an unhandled runtime error state.',
      icon: '💥'
    }
  };

  ngOnInit(): void {
    // Read the explicitly passed code parameter or path segment state fallback
    this.statusCode = this.route.snapshot.data['code'] || '404';
    this.config = this.errorSpecs[this.statusCode] || this.errorSpecs['404'];
  }

  goBack(): void {
  this.routery.navigate(['/']);
}
}