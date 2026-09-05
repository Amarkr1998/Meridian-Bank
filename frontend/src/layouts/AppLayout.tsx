import { useState, type ReactNode } from 'react';
import {
  AppBar,
  Avatar,
  Badge,
  Box,
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
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import DashboardIcon from '@mui/icons-material/SpaceDashboard';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import GroupIcon from '@mui/icons-material/Group';
import SendIcon from '@mui/icons-material/Send';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import DescriptionIcon from '@mui/icons-material/Description';
import SupportAgentIcon from '@mui/icons-material/SupportAgent';
import NotificationsIcon from '@mui/icons-material/Notifications';
import PersonIcon from '@mui/icons-material/Person';
import ShieldIcon from '@mui/icons-material/Shield';
import LogoutIcon from '@mui/icons-material/Logout';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useNotifications } from '../api/notifications';

const DRAWER_WIDTH = 248;

const NAV_ITEMS = [
  { label: 'Dashboard', path: '/dashboard', icon: <DashboardIcon /> },
  { label: 'Accounts', path: '/accounts', icon: <CreditCardIcon /> },
  { label: 'Beneficiaries', path: '/beneficiaries', icon: <GroupIcon /> },
  { label: 'Payments', path: '/payments', icon: <SendIcon /> },
  { label: 'Transactions', path: '/transactions', icon: <ReceiptLongIcon /> },
  { label: 'Statements', path: '/statements', icon: <DescriptionIcon /> },
  { label: 'Support', path: '/support', icon: <SupportAgentIcon /> },
];

export function AppLayout({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { data: notifications } = useNotifications();
  const unreadCount = notifications?.filter((n) => !n.read).length ?? 0;

  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);

  async function handleLogout() {
    setMenuAnchor(null);
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 2 }}>
          <AccountBalanceIcon />
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 700 }}>
            Meridian Digital Banking
          </Typography>
          <IconButton color="inherit" component={RouterLink} to="/notifications">
            <Badge badgeContent={unreadCount} color="error">
              <NotificationsIcon />
            </Badge>
          </IconButton>
          <IconButton onClick={(e) => setMenuAnchor(e.currentTarget)} sx={{ ml: 1 }}>
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
            <MenuItem component={RouterLink} to="/security" onClick={() => setMenuAnchor(null)}>
              <ListItemIcon>
                <ShieldIcon fontSize="small" />
              </ListItemIcon>
              Security
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

      <Box component="main" sx={{ flexGrow: 1, p: { xs: 2, sm: 4 } }}>
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
}
