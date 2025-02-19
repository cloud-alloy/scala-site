/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [".src/**/*.scala"],
  safelist: [{ pattern: /.*/ }], // Include everything
  theme: { extend: {} },
  corePlugins: { preflight: false },
  plugins: [],
};

