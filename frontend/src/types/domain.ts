// Mirrors backend DTOs exactly (see each service's web/dto package) — no fields invented here.

export type UserRole = 'CUSTOMER' | 'OPERATIONS' | 'COMPLIANCE_OFFICER' | 'RISK_ANALYST' | 'AUDITOR' | 'ADMIN';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  role: UserRole;
}

export interface LoginResponse {
  mfaRequired: boolean;
  mfaChallengeId?: string;
  expiresInSeconds?: number;
  devOtp?: string;
  tokens?: TokenResponse;
}

export interface MeResponse {
  userId: string;
  email: string;
  role: UserRole;
  status: string;
  mfaEnabled: boolean;
}

export interface SessionResponse {
  id: string;
  issuedAt: string;
  expiresAt: string;
  createdByIp: string;
}

// --- customer-kyc-service ---

export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED' | 'SUSPENDED';

export interface RegisterCustomerRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export interface RegisterCustomerResponse {
  customerId: string;
  email: string;
  contactVerified: boolean;
  verificationExpiresInSeconds: number;
  devOtp?: string;
}

export interface CustomerResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  status: CustomerStatus;
  contactVerified: boolean;
  createdAt: string;
}

export type DocumentType = 'NATIONAL_ID' | 'PASSPORT' | 'DRIVERS_LICENSE' | 'PROOF_OF_ADDRESS';

export interface DocumentEntry {
  documentType: DocumentType;
  documentReference: string;
}

export interface DocumentResponse {
  id: string;
  documentType: DocumentType;
  documentReference: string;
  uploadedAt: string;
}

export type KycStatus = 'KYC_PENDING' | 'KYC_IN_REVIEW' | 'KYC_VERIFIED' | 'KYC_REJECTED';

export interface SubmitKycRequest {
  nationality: string;
  occupation: string;
  documents: DocumentEntry[];
}

export interface KycRecordResponse {
  id: string;
  customerId: string;
  status: KycStatus;
  nationality: string;
  occupation: string;
  submittedAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  rejectionReason?: string;
  documents: DocumentResponse[];
}

export type SupportRequestCategory = 'ACCOUNT' | 'PAYMENT' | 'KYC' | 'FRAUD' | 'GENERAL';
export type SupportRequestStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';

export interface CreateSupportRequestRequest {
  category: SupportRequestCategory;
  subject: string;
  description: string;
}

export interface SupportRequestResponse {
  id: string;
  customerId: string;
  category: SupportRequestCategory;
  subject: string;
  description: string;
  status: SupportRequestStatus;
  assignedTo?: string;
  assignedAt?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  resolutionNotes?: string;
  createdAt: string;
  updatedAt: string;
}

// --- account-service ---

export type AccountType = 'SAVINGS' | 'CURRENT';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'BLOCKED' | 'CLOSED';
export type AccountOpeningStatus = 'ACCOUNT_REQUESTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED';

export interface AccountResponse {
  id: string;
  maskedAccountNumber: string;
  accountType: AccountType;
  status: AccountStatus;
  currency: string;
  perTransactionLimit: number;
  dailyLimit: number;
  openedAt?: string;
  closedAt?: string;
  availableBalance?: number;
  ledgerBalance?: number;
}

export interface AccountRequestResponse {
  id: string;
  customerId: string;
  accountType: AccountType;
  status: AccountOpeningStatus;
  requestedAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  rejectionReason?: string;
  accountId?: string;
}

export type BeneficiaryStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'BLOCKED';

export interface AddBeneficiaryRequest {
  nickname: string;
  beneficiaryName: string;
  beneficiaryAccountNumber: string;
}

export interface BeneficiaryResponse {
  id: string;
  nickname: string;
  beneficiaryName: string;
  maskedBeneficiaryAccountNumber: string;
  status: BeneficiaryStatus;
  activatedAt?: string;
  createdAt: string;
  destinationAccountId?: string;
  destinationAccountStatus?: AccountStatus;
}

export interface AddBeneficiaryResponse {
  beneficiary: BeneficiaryResponse;
  expiresInSeconds: number;
  devOtp?: string;
}

// --- payment-service ---

export type TransactionStatus = 'INITIATED' | 'VALIDATING' | 'RISK_CHECK' | 'PROCESSING' | 'SUCCESS' | 'FAILED';

export interface CreatePaymentRequest {
  sourceAccountId: string;
  beneficiaryId: string;
  amount: number;
  currency: string;
  purpose?: string;
}

export interface PaymentResponse {
  id: string;
  sourceAccountId: string;
  beneficiaryId: string;
  destinationAccountId?: string;
  amount: number;
  currency: string;
  purpose?: string;
  status: TransactionStatus;
  failureCode?: string;
  failureReason?: string;
  createdAt: string;
  completedAt?: string;
}

// --- notification-service ---

export type NotificationType =
  | 'PAYMENT_SUCCESS'
  | 'PAYMENT_FAILED'
  | 'LOGIN_ALERT'
  | 'SECURITY_ALERT'
  | 'KYC_STATUS_CHANGED'
  | 'ACCOUNT_STATUS_CHANGED'
  | 'FRAUD_ALERT'
  | 'SUPPORT_REQUEST_RESOLVED';

export interface NotificationResponse {
  id: string;
  customerId: string;
  type: NotificationType;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
}

// --- shared ---

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  correlationId?: string;
}

// --- Phase 15: Operations & Compliance ---

export type FraudAlertStatus = 'OPEN' | 'IN_REVIEW' | 'CLEARED' | 'ESCALATED' | 'CONFIRMED_FRAUD';
export type FraudDecision = 'ALLOW' | 'REVIEW' | 'BLOCK';

export interface FraudAlertResponse {
  id: string;
  transactionId: string;
  customerId: string;
  sourceAccountId: string;
  destinationAccountId?: string;
  amount: number;
  currency: string;
  score: number;
  decision: FraudDecision;
  ruleHits?: string;
  status: FraudAlertStatus;
  createdAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  resolutionNotes?: string;
}

export type AmlAlertStatus = 'OPEN' | 'IN_REVIEW' | 'CLEARED' | 'ESCALATED';

export interface AmlAlertResponse {
  id: string;
  transactionId: string;
  customerId: string;
  signalCode: string;
  amount: number;
  currency: string;
  status: AmlAlertStatus;
  details?: string;
  createdAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  resolutionNotes?: string;
}

export interface FraudRuleResponse {
  id: string;
  ruleCode: string;
  category: string;
  description: string;
  weight: number;
  thresholdNumeric?: number;
  thresholdWindowSeconds?: number;
  thresholdCount?: number;
  enabled: boolean;
  updatedAt?: string;
  updatedBy?: string;
}

export interface UpdateFraudRuleRequest {
  weight: number;
  thresholdNumeric: number;
  thresholdWindowSeconds?: number;
  thresholdCount?: number;
  enabled: boolean;
}

export interface ResolutionRequest {
  notes?: string;
}

export type ReconciliationStatus = 'MATCHED' | 'MISMATCHED' | 'PENDING' | 'INVESTIGATION' | 'RESOLVED';

export interface ReconciliationRecordResponse {
  id: string;
  transactionId: string;
  internalAmount: number;
  internalCurrency: string;
  externalTransactionId?: string;
  externalAmount?: number;
  externalCurrency?: string;
  status: ReconciliationStatus;
  mismatchReason?: string;
  investigatedBy?: string;
  investigatedAt?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  resolutionNotes?: string;
  runId: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuditEventResponse {
  id: string;
  eventId: string;
  eventType: string;
  occurredAt: string;
  correlationId?: string;
  producedBy: string;
  actorId?: string;
  actorRole?: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  result?: string;
  detail?: string;
  receivedAt: string;
}

export type ApprovalStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

/** A union of the four (near-identical, independently-owned) gating services' approval DTOs. */
export interface ApprovalRequestResponse {
  id: string;
  actionType: string;
  resourceId: string;
  status: ApprovalStatus;
  requestedBy: string;
  requestedAt: string;
  decidedBy?: string;
  decidedAt?: string;
  decisionNotes?: string;
  reason?: string;
  requestedStatus?: string;
  payload?: string;
  resultTransactionId?: string;
}

export interface ApprovalDecisionRequest {
  notes?: string;
}

export interface UpdateCustomerStatusRequest {
  status: CustomerStatus;
  reason: string;
}

export interface AccountStatusChangeRequest {
  reason: string;
}

export interface UpdateAccountLimitsRequest {
  perTransactionLimit: number;
  dailyLimit: number;
}

export interface RejectRequest {
  reason: string;
}

export interface ServiceHealth {
  service: string;
  status: string;
}
