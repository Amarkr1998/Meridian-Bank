import { useState, type ReactNode } from 'react';
import {
  AppBar,
  Avatar,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material';
import ShieldIcon from '@mui/icons-material/AdminPanelSettings';
import DashboardIcon from '@mui/icons-material/SpaceDashboard';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import GppMaybeIcon from '@mui/icons-material/GppMaybe';
import PolicyIcon from '@mui/icons-material/Policy';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import GroupIcon from '@mui/icons-material/Group';
import HowToRegIcon from '@mui/icons-material/HowToReg';
import BalanceIcon from '@mui/icons-material/Balance';
import HistoryEduIcon from '@mui/icons-material/HistoryEdu';
import SupportAgentIcon from '@mui/icons-material/SupportAgent';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import PersonIcon from '@mui/icons-material/Person';
import LogoutIcon from '@mui/icons-material/Logout';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const DRAWER_WIDTH = 252;

const NAV_ITEMS = [
  { label: 'Dashboard', path: '/ops/dashboard', icon: <DashboardIcon /> },
  { label: 'Transactions', path: '/ops/transactions', icon: <ReceiptLongIcon /> },
  { label: 'Fraud', path: '/ops/fraud', icon: <GppMaybeIcon /> },
  { label: 'AML', path: '/ops/aml', icon: <PolicyIcon /> },
  { label: 'KYC Review', path: '/ops/kyc', icon: <FactCheckIcon /> },
  { label: 'Accounts', path: '/ops/accounts', icon: <CreditCardIcon /> },
  { label: 'Customers', path: '/ops/customers', icon: <GroupIcon /> },
  { label: 'Approvals', path: '/ops/approvals', icon: <HowToRegIcon /> },
  { label: 'Reconciliation', path: '/ops/reconciliation', icon: <BalanceIcon /> },
  { label: 'Audit Trail', path: '/ops/audit', icon: <HistoryEduIcon /> },
  { label: 'Support', path: '/ops/support', icon: <SupportAgentIcon /> },
  { label: 'System Health', path: '/ops/system-health', icon: <MonitorHeartIcon /> },
];

export function OpsLayout({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);

  async function handleLogout() {
    setMenuAnchor(null);
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <Stack sx={{ minHeight: '100vh', bgcolor: 'background.default' }} direction="row">
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1, bgcolor: '#061529' }}>
        <Toolbar sx={{ gap: 2 }}>
          <ShieldIcon />
          <Stack sx={{ flexGrow: 1 }}>
            <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.1 }}>
              Meridian Operations &amp; Compliance
            </Typography>
            <Typography variant="caption" sx={{ opacity: 0.75 }}>
              Internal staff portal
            </Typography>
          </Stack>
          <IconButton onClick={(e) => setMenuAnchor(e.currentTarget)}>
            <Avatar sx={{ width: 32, height: 32, bgcolor: 'secondary.main', fontSize: 14 }}>
              {user?.email.slice(0, 2).toUpperCase()}
            </Avatar>
          </IconButton>
          <Menu anchorEl={menuAnchor} open={!!menuAnchor} onClose={() => setMenuAnchor(null)}>
            <MenuItem disabled sx={{ opacity: '1 !important' }}>
              <Stack>
                <Typography variant="body2" fontWeight={600}>
                  {user?.email}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {user?.role}
                </Typography>
              </Stack>
            </MenuItem>
            <Divider />
            <MenuItem component={RouterLink} to="/profile" onClick={() => setMenuAnchor(null)}>
              <ListItemIcon>
                <PersonIcon fontSize="small" />
              </ListItemIcon>
              Profile
            </MenuItem>
            <Divider />
            <MenuItem onClick={handleLogout}>
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              Log out
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: 'border-box', borderRight: '1px solid #E2E6EB' },
        }}
      >
        <Toolbar />
        <List sx={{ px: 1, py: 2 }}>
          {NAV_ITEMS.map((item) => (
            <ListItemButton
              key={item.path}
              component={RouterLink}
              to={item.path}
              selected={location.pathname.startsWith(item.path)}
              sx={{ borderRadius: 1.5, mb: 0.5 }}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>

      <Stack component="main" sx={{ flexGrow: 1, p: { xs: 2, sm: 4 }, minWidth: 0 }}>
        <Toolbar />
        {children}
      </Stack>
    </Stack>
  );
}
