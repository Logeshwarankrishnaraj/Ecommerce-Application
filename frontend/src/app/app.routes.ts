import { Routes } from '@angular/router';
import { customerGuard } from './core/auth/auth.guard';
import { adminGuard } from './core/auth/role.guard';
import { CustomerLayoutComponent } from './core/layout/customer-layout/customer-layout.component';

/** Route guards are a UX aid; every API call is authorized again on the server. */
export const routes: Routes = [
  // The admin area must be declared BEFORE the storefront layout, whose catch-all ('**') would otherwise swallow /admin/*.
  // Separate staff sign-in: not linked from anywhere in the storefront and outside the admin guard.
  {
    path: 'admin/login',
    title: 'Staff sign in',
    loadComponent: () => import('./features/admin/login/admin-login.component').then((m) => m.AdminLoginComponent),
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin/layout/admin-layout.component').then((m) => m.AdminLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./features/admin/dashboard/dashboard.component').then((m) => m.DashboardComponent), title: 'Dashboard' },
      { path: 'products', loadComponent: () => import('./features/admin/products/products.component').then((m) => m.ProductsComponent), title: 'Products' },
      { path: 'products/:id', loadComponent: () => import('./features/admin/product-editor/product-editor.component').then((m) => m.ProductEditorComponent), title: 'Product' },
      { path: 'categories', loadComponent: () => import('./features/admin/categories/categories.component').then((m) => m.CategoriesComponent), title: 'Categories' },
      { path: 'lookups', loadComponent: () => import('./features/admin/lookups/lookups.component').then((m) => m.LookupsComponent), title: 'Filter values' },
      { path: 'inventory', loadComponent: () => import('./features/admin/inventory/inventory.component').then((m) => m.InventoryComponent), title: 'Inventory' },
      { path: 'orders', loadComponent: () => import('./features/admin/orders/orders.component').then((m) => m.OrdersAdminComponent), title: 'Orders' },
      { path: 'orders/:id', loadComponent: () => import('./features/admin/orders/order-detail.component').then((m) => m.OrderDetailAdminComponent), title: 'Order' },
      { path: 'imports', loadComponent: () => import('./features/admin/imports/imports.component').then((m) => m.ImportsComponent), title: 'Bulk import' },
      { path: 'analytics', loadComponent: () => import('./features/admin/analytics/analytics.component').then((m) => m.AnalyticsComponent), title: 'Analytics' },
      { path: 'shop-settings', loadComponent: () => import('./features/admin/shop-settings/shop-settings.component').then((m) => m.ShopSettingsComponent), title: 'Shop & settings' },
      { path: 'site-builder', loadComponent: () => import('./features/admin/site-builder/site-builder.component').then((m) => m.SiteBuilderComponent), title: 'Site builder' },
      { path: 'errors', loadComponent: () => import('./features/admin/errors/errors.component').then((m) => m.ErrorsComponent), title: 'Error center' },
      { path: 'users', loadComponent: () => import('./features/admin/users/users.component').then((m) => m.UsersComponent), title: 'Users' },
      { path: 'audit', loadComponent: () => import('./features/admin/audit/audit.component').then((m) => m.AuditComponent), title: 'Audit log' },
      { path: '**', loadComponent: () => import('./features/storefront/pages/not-found/not-found.component').then((m) => m.NotFoundComponent), title: 'Not found' },
    ],
  },
  {
    path: '',
    component: CustomerLayoutComponent,
    children: [
      { path: '', loadComponent: () => import('./features/storefront/pages/home/home.component').then((m) => m.HomeComponent), title: 'Home' },
      { path: 'products', loadComponent: () => import('./features/catalog/pages/product-list.component').then((m) => m.ProductListComponent), title: 'Shop' },
      { path: 'products/:slug', loadComponent: () => import('./features/catalog/pages/product-detail.component').then((m) => m.ProductDetailComponent) },
      { path: 'pages/:slug', loadComponent: () => import('./features/storefront/pages/info-page/info-page.component').then((m) => m.InfoPageComponent) },
      { path: 'diagnostics', loadComponent: () => import('./features/diagnostics/diagnostics.component').then((m) => m.DiagnosticsComponent), title: 'Connection check' },
      { path: 'login', loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent), title: 'Sign in' },
      { path: 'register', loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent), title: 'Create account' },
      { path: 'forgot-password', loadComponent: () => import('./features/auth/forgot-password.component').then((m) => m.ForgotPasswordComponent), title: 'Reset password' },
      { path: 'cart', loadComponent: () => import('./features/cart/pages/cart.component').then((m) => m.CartComponent), title: 'Cart' },
      { path: 'wishlist', canActivate: [customerGuard], loadComponent: () => import('./features/wishlist/pages/wishlist.component').then((m) => m.WishlistComponent), title: 'Wishlist' },
      { path: 'checkout', canActivate: [customerGuard], loadComponent: () => import('./features/checkout/pages/checkout.component').then((m) => m.CheckoutComponent), title: 'Checkout' },
      { path: 'account/profile', canActivate: [customerGuard], loadComponent: () => import('./features/account/pages/profile.component').then((m) => m.ProfileComponent), title: 'Profile' },
      { path: 'account/addresses', canActivate: [customerGuard], loadComponent: () => import('./features/account/pages/addresses.component').then((m) => m.AddressesComponent), title: 'Addresses' },
      { path: 'account/orders', canActivate: [customerGuard], loadComponent: () => import('./features/account/pages/orders.component').then((m) => m.OrdersComponent), title: 'Orders' },
      { path: 'account/orders/:orderId', canActivate: [customerGuard], loadComponent: () => import('./features/account/pages/order-detail.component').then((m) => m.OrderDetailComponent), title: 'Order' },
      { path: '**', loadComponent: () => import('./features/storefront/pages/not-found/not-found.component').then((m) => m.NotFoundComponent), title: 'Not found' },
    ],
  },
];
