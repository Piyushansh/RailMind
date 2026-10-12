/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // "Control tower" palette - a rail dispatch board, not a generic SaaS kit.
        paper: '#F6F7F9',
        ink: '#12181F',
        slate: {
          50: '#F6F7F9',
          100: '#EDEFF2',
          200: '#E2E6EB',
          300: '#C9CFD6',
          400: '#9AA3AD',
          500: '#6B7480',
          600: '#4C5560',
          700: '#333B44',
          800: '#1F252C',
          900: '#12181F',
        },
        signal: {
          DEFAULT: '#2F6FED',
          hover: '#2559C7',
          soft: '#E8EFFE',
        },
        amber: {
          DEFAULT: '#F2A93B',
          soft: '#FDF1DD',
        },
        rail: {
          DEFAULT: '#1F9D6C',
          soft: '#E4F6EE',
        },
        danger: {
          DEFAULT: '#D5432C',
          soft: '#FBEAE7',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      borderRadius: {
        sharp: '2px',
        card: '10px',
      },
    },
  },
  plugins: [],
};
