import type { Config } from 'tailwindcss';

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#f2f7ff',
          100: '#e0ecff',
          200: '#c2d9ff',
          300: '#94bcff',
          400: '#5f96ff',
          500: '#3670ff',
          600: '#1f4fed',
          700: '#1a3ec2',
          800: '#1a349a',
          900: '#1b307a',
        },
        surface: {
          light: '#ffffff',
          dark: '#0f1115',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
} satisfies Config;
