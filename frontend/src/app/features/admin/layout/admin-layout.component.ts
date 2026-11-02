import { IconComponent } from '../../../shared/components/icon/icon.component';
import { Component, ViewEncapsulation, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogComponent } from '../shared/confirm-dialog.component';
import { TooltipDirective } from '../shared/tooltip.directive';

interface NavItem { label: string; icon: string; path: string; exact?: boolean; }

import { ThemeToggleComponent } from '../../../shared/components/theme-toggle/theme-toggle.component';
@Component({
  selector: 'admin-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ConfirmDialogComponent, TooltipDirective, IconComponent, ThemeToggleComponent],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.css',
  // Global-to-admin primitives (.card, .tbl, .btn, .field ...) are defined under .admin-shell.
  encapsulation: ViewEncapsulation.None,
})
export class AdminLayoutComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly navOpen = signal(false);

  protected readonly nav: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', path: '/admin', exact: true },
    { label: 'Products', icon: 'package', path: '/admin/products' },
    { label: 'Categories', icon: 'folder', path: '/admin/categories' },
    { label: 'Filter values', icon: 'sliders', path: '/admin/lookups' },
    { label: 'Inventory', icon: 'archive', path: '/admin/inventory' },
    { label: 'Orders', icon: 'receipt', path: '/admin/orders' },
    { label: 'Bulk import', icon: 'upload', path: '/admin/imports' },
    { label: 'Analytics', icon: 'chart', path: '/admin/analytics' },
    { label: 'Shop & settings', icon: 'store', path: '/admin/shop-settings' },
    { label: 'Site builder', icon: 'layout', path: '/admin/site-builder' },
    { label: 'Error center', icon: 'alert', path: '/admin/errors' },
    { label: 'Users', icon: 'users', path: '/admin/users' },
    { label: 'Audit log', icon: 'clipboard', path: '/admin/audit' },
  ];

  protected toggleNav(): void { this.navOpen.update((v) => !v); }
  protected closeNav(): void { this.navOpen.set(false); }

  protected logout(): void {
    this.auth.logout().subscribe({ complete: () => void this.router.navigateByUrl('/admin/login') });
  }
}
