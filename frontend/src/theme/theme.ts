import { createTheme } from '@mui/material/styles';

/**
 * Meridian's palette: deep navy + a restrained blue accent, neutral grays for surfaces —
 * deliberately not a generic Bootstrap-admin blue or a flashy fintech gradient. See
 * CLAUDE.md: "Must look like a real commercial banking application."
 */
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#0B2545',
      light: '#1B3A6B',
      dark: '#061529',
      contrastText: '#FFFFFF',
    },
    secondary: {
      main: '#2E6F9E',
      contrastText: '#FFFFFF',
    },
    background: {
      default: '#F4F6F8',
      paper: '#FFFFFF',
    },
    text: {
      primary: '#14213D',
      secondary: '#5A6472',
    },
    success: {
      main: '#1E7B45',
    },
    warning: {
      main: '#B7791F',
    },
    error: {
      main: '#B3261E',
    },
    divider: '#E2E6EB',
  },
  shape: {
    borderRadius: 8,
  },
  typography: {
    fontFamily: '"Inter", "Segoe UI", Roboto, -apple-system, BlinkMacSystemFont, sans-serif',
    h1: { fontWeight: 700 },
    h2: { fontWeight: 700 },
    h3: { fontWeight: 600 },
    h4: { fontWeight: 600 },
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
    button: { fontWeight: 600, textTransform: 'none' },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: { borderRadius: 6 },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: '#0B2545',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          border: '1px solid #E2E6EB',
          boxShadow: 'none',
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600 },
      },
    },
  },
});
