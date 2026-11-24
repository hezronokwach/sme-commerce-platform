// Settings for the production build (`npm run build`).
export const environment = {
  // Empty means "the same address as this page". Deployed on Vercel, vercel.json passes /api on to
  // the backend, so the refresh cookie stays on the admin's own site (ADR 0018).
  apiBaseUrl: '',
  // The customer storefront (another Vercel project): the home page, and where each shop's page lives.
  storefrontUrl: 'https://sme-commerce-platform-storefront.vercel.app',
};
