import { Route, Routes } from 'react-router-dom';
import { RequireCustomer, RequireStaff } from './auth/RequireRole';
import { Login } from './pages/Login';
import { Register } from './pages/Register';
import { MfaVerify } from './pages/MfaVerify';
import { Onboarding } from './pages/Onboarding';
import { Dashboard } from './pages/Dashboard';
import { Accounts } from './pages/Accounts';
import { AccountDetail } from './pages/AccountDetail';
import { Beneficiaries } from './pages/Beneficiaries';
import { Payments } from './pages/Payments';
import { Transactions } from './pages/Transactions';
import { Statements } from './pages/Statements';
import { Support } from './pages/Support';
import { SupportDetail } from './pages/SupportDetail';
import { Notifications } from './pages/Notifications';
import { Profile } from './pages/Profile';
import { Security } from './pages/Security';
import { OpsDashboard } from './pages/ops/OpsDashboard';
import { OpsTransactions } from './pages/ops/OpsTransactions';
import { OpsFraud } from './pages/ops/OpsFraud';
import { OpsAml } from './pages/ops/OpsAml';
import { OpsKyc } from './pages/ops/OpsKyc';
import { OpsAccounts } from './pages/ops/OpsAccounts';
import { OpsCustomers } from './pages/ops/OpsCustomers';
import { OpsApprovals } from './pages/ops/OpsApprovals';
import { OpsReconciliation } from './pages/ops/OpsReconciliation';
import { OpsAudit } from './pages/ops/OpsAudit';
import { OpsSupport } from './pages/ops/OpsSupport';
import { OpsSystemHealth } from './pages/ops/OpsSystemHealth';
import { RootRedirect } from './RootRedirect';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/mfa" element={<MfaVerify />} />

      {/* Customer portal — Meridian Digital Banking */}
      <Route path="/onboarding" element={<RequireCustomer><Onboarding /></RequireCustomer>} />
      <Route path="/dashboard" element={<RequireCustomer><Dashboard /></RequireCustomer>} />
      <Route path="/accounts" element={<RequireCustomer><Accounts /></RequireCustomer>} />
      <Route path="/accounts/:accountId" element={<RequireCustomer><AccountDetail /></RequireCustomer>} />
      <Route path="/beneficiaries" element={<RequireCustomer><Beneficiaries /></RequireCustomer>} />
      <Route path="/payments" element={<RequireCustomer><Payments /></RequireCustomer>} />
      <Route path="/transactions" element={<RequireCustomer><Transactions /></RequireCustomer>} />
      <Route path="/statements" element={<RequireCustomer><Statements /></RequireCustomer>} />
      <Route path="/support" element={<RequireCustomer><Support /></RequireCustomer>} />
      <Route path="/support/:requestId" element={<RequireCustomer><SupportDetail /></RequireCustomer>} />
      <Route path="/notifications" element={<RequireCustomer><Notifications /></RequireCustomer>} />
      <Route path="/profile" element={<RequireCustomer><Profile /></RequireCustomer>} />
      <Route path="/security" element={<RequireCustomer><Security /></RequireCustomer>} />

      {/* Operations & Compliance portal — staff only */}
      <Route path="/ops/dashboard" element={<RequireStaff><OpsDashboard /></RequireStaff>} />
      <Route path="/ops/transactions" element={<RequireStaff><OpsTransactions /></RequireStaff>} />
      <Route path="/ops/fraud" element={<RequireStaff><OpsFraud /></RequireStaff>} />
      <Route path="/ops/aml" element={<RequireStaff><OpsAml /></RequireStaff>} />
      <Route path="/ops/kyc" element={<RequireStaff><OpsKyc /></RequireStaff>} />
      <Route path="/ops/accounts" element={<RequireStaff><OpsAccounts /></RequireStaff>} />
      <Route path="/ops/customers" element={<RequireStaff><OpsCustomers /></RequireStaff>} />
      <Route path="/ops/approvals" element={<RequireStaff><OpsApprovals /></RequireStaff>} />
      <Route path="/ops/reconciliation" element={<RequireStaff><OpsReconciliation /></RequireStaff>} />
      <Route path="/ops/audit" element={<RequireStaff><OpsAudit /></RequireStaff>} />
      <Route path="/ops/support" element={<RequireStaff><OpsSupport /></RequireStaff>} />
      <Route path="/ops/system-health" element={<RequireStaff><OpsSystemHealth /></RequireStaff>} />

      <Route path="/" element={<RootRedirect />} />
      <Route path="*" element={<RootRedirect />} />
    </Routes>
  );
}
