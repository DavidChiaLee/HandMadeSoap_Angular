/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#fdf8f3',
          100: '#f9ebe0',
          200: '#f2d5bd',
          300: '#e9b88f',
          400: '#de9a60',
          500: '#d48042',
          600: '#c66a37',
          700: '#a6532e',
          800: '#85442b',
          900: '#6c3926',
        },
        accent: {
          50: '#f4f9f4',
          100: '#e6f2e6',
          200: '#cee5ce',
          300: '#a6d0a6',
          400: '#76b376',
          500: '#529552',
          600: '#3f7a3f',
          700: '#346134',
          800: '#2c4e2c',
          900: '#254125',
        }
      }
    },
  },
  plugins: [],
}
