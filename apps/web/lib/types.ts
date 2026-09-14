/** Mirrors the DTOs the services return. Kept by hand — the OpenAPI documents
 *  at /v3/api-docs are the source of truth if these ever disagree. */

export type ListingKind =
  | 'RENT_ITEM'
  | 'SELL_ITEM'
  | 'TRADE_SERVICE'
  | 'SKILL_FOR_HIRE'
  | 'TEACHING'
  | 'SPACE_OR_VEHICLE'
  | 'OPEN_NEED';

export type Unit = 'HOUR' | 'DAY' | 'WEEK' | 'SESSION' | 'ITEM';

export type RequestStatus =
  | 'SENT'
  | 'ACCEPTED'
  | 'DECLINED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED';

export interface TagRef {
  id: string;
  slug: string;
  label: string;
  kind: string;
  relation: string | null;
}

export interface Me {
  id: string;
  displayName: string;
  phone: string | null;
  photoUrl: string | null;
  areaLabel: string | null;
  lat: number | null;
  lon: number | null;
  searchRadiusKm: number;
  openToRequests: boolean;
  bio: string | null;
  ratingAvg: number | null;
  completedCount: number;
  /** "We have seen ID", not "we vouch for them". */
  idChecked: boolean;
  idCheckedAt: string | null;
  professional: boolean;
  role: "MEMBER" | "ADMIN";
  tags: TagRef[];
}

export interface PublicProfile {
  id: string;
  displayName: string;
  photoUrl: string | null;
  areaLabel: string | null;
  bio: string | null;
  ratingAvg: number | null;
  completedCount: number;
  idChecked: boolean;
  idCheckedAt: string | null;
  professional: boolean;
  trade: string | null;
  businessName: string | null;
  openToRequests: boolean;
  tags: TagRef[];
  distanceKm: number | null;
}

export interface Owner {
  id: string;
  displayName: string | null;
  photoUrl: string | null;
  areaLabel: string | null;
  idChecked: boolean;
  professional: boolean;
  trade: string | null;
}

export interface ListingCard {
  id: string;
  kind: ListingKind;
  title: string;
  priceMinor: number | null;
  unit: Unit | null;
  buyPriceMinor: number | null;
  currency: string;
  photoUrl: string | null;
  lat: number;
  lon: number;
  distanceKm: number;
  owner: Owner;
  status: string;
  homeVisit: boolean;
  /** Null until somebody rates. Zero would read as a rating of zero. */
  ratingAvg: number | null;
  ratingCount: number;
}

export interface Photo {
  id: string;
  url: string;
  sortOrder: number;
}

export interface AvailabilityRule {
  weekday: number;
  from: string;
  to: string;
}

export interface Listing {
  id: string;
  kind: ListingKind;
  title: string;
  description: string | null;
  priceMinor: number | null;
  unit: Unit | null;
  depositMinor: number;
  buyPriceMinor: number | null;
  currency: string;
  lat: number;
  lon: number;
  status: string;
  tags: string[];
  photos: Photo[];
  availability: AvailabilityRule[];
  owner: Owner;
  distanceKm: number | null;
  mine: boolean;
  homeVisit: boolean;
  ratingAvg: number | null;
  ratingCount: number;
}

/* ---- the conversation around a listing --------------------------------- */

export interface CommentView {
  id: string;
  listingId: string;
  authorId: string;
  /** Null once removed — the tombstone keeps its place in the thread. */
  authorName: string | null;
  parentId: string | null;
  body: string | null;
  deleted: boolean;
  createdAt: string;
  editedAt: string | null;
  /** Resolved server-side. The client renders these, it does not re-derive them. */
  canEdit: boolean;
  canDelete: boolean;
  replies: CommentView[];
}

export interface RatingView {
  id: string;
  listingId: string;
  authorId: string;
  authorName: string | null;
  stars: number;
  title: string | null;
  body: string | null;
  /** Left by someone who actually completed a booking. */
  verifiedBooking: boolean;
  createdAt: string;
  editedAt: string | null;
  canEdit: boolean;
  canDelete: boolean;
}

export interface RatingSummary {
  average: number | null;
  count: number;
  /** Star → how many. Stars nobody gave are absent, not zero. */
  histogram: Record<string, number>;
  mine: RatingView | null;
  ratings: RatingView[];
}

export type ReportTargetType = 'LISTING' | 'COMMENT' | 'RATING';

export type ReportState = 'OPEN' | 'REVIEWING' | 'ACTIONED' | 'DISMISSED';

export interface ReportView {
  id: string;
  reporterId: string;
  targetType: ReportTargetType;
  targetId: string;
  reason: string;
  detail: string | null;
  state: ReportState;
  createdAt: string;
  decidedBy: string | null;
  decidedAt: string | null;
  note: string | null;
  /** The reported content itself, so a decision needs no second request. */
  targetSummary: string | null;
  targetAuthorId: string | null;
}

export interface BanView {
  userId: string;
  bannedUntil: string;
  reason: string;
  setBy: string;
  setAt: string;
}

export interface FeedPage {
  items: ListingCard[];
  nextCursor: string | null;
  radiusKm: number;
}

export interface Breakdown {
  rateMinor: number;
  unit: string;
  units: number;
  amountMinor: number;
  depositMinor: number;
  feeMinor: number;
  feeLabel: string;
  totalMinor: number;
  currency: string;
  settlementNote: string;
}

export interface Transition {
  from: string | null;
  to: string;
  actorId: string | null;
  reason: string | null;
  at: string;
}

export interface BookingRequest {
  id: string;
  listingId: string;
  listingTitle: string;
  requesterId: string;
  ownerId: string;
  startDate: string;
  endDate: string;
  units: number;
  unit: string;
  message: string | null;
  status: RequestStatus;
  breakdown: Breakdown;
  createdAt: string;
  expiresAt: string;
  unreadCount: number;
  iAmOwner: boolean;
  history: Transition[];
}

export interface ChatMessage {
  id: string;
  requestId: string;
  senderId: string;
  body: string;
  sentAt: string;
  readAt: string | null;
}

export interface ParsedQuery {
  terms: string[];
  tags: string[];
  kinds: string[];
  radiusKm: number | null;
  day: string | null;
  source: string;
}

export interface SearchListingHit {
  id: string;
  kind: ListingKind;
  title: string;
  description: string | null;
  priceMinor: number | null;
  unit: Unit | null;
  buyPriceMinor: number | null;
  currency: string;
  photoUrl: string | null;
  lat: number;
  lon: number;
  distanceKm: number;
  ownerId: string;
  ownerName: string | null;
  ownerPhoto: string | null;
  ownerRating: number | null;
  areaLabel: string | null;
  relevance: number;
}

export interface SearchMemberHit {
  id: string;
  displayName: string | null;
  photoUrl: string | null;
  areaLabel: string | null;
  bio: string | null;
  rating: number | null;
  distanceKm: number;
  tags: string[];
}

export interface SearchResponse {
  parsed: ParsedQuery;
  listings: SearchListingHit[];
  members: SearchMemberHit[];
  radiusKm: number;
  hasMore: boolean;
}

export interface MapResponse {
  pins: { id: string; lat: number; lon: number; priceMinor: number | null; kind: ListingKind; title: string }[];
  clusters: { lat: number; lon: number; count: number }[];
  total: number;
  clustered: boolean;
}

export interface AppNotification {
  id: string;
  kind: string;
  title: string;
  body: string | null;
  deepLink: string | null;
  state: string;
  createdAt: string;
  readAt: string | null;
}

export interface Tokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  me: Me;
}

/* ---- verification and professionals ------------------------------------ */

export type VerificationKind = 'IDENTITY' | 'PROFESSIONAL';

export type VerificationState =
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'NONE';

export interface VerificationStatus {
  id: string;
  kind: VerificationKind;
  state: VerificationState;
  memberNote: string | null;
  /** Why it was refused — the member needs this to fix it and re-apply. */
  decisionNote: string | null;
  submittedAt: string;
  decidedAt: string | null;
  open: boolean;
}

export interface MyVerification {
  idChecked: boolean;
  idCheckedAt: string | null;
  identityState: VerificationState;
  professionalState: VerificationState;
  canGoProfessional: boolean;
  isProfessional: boolean;
  history: VerificationStatus[];
}

export const TRADES = [
  'ELECTRICIAN',
  'PLUMBER',
  'AC_SERVICE',
  'APPLIANCE_REPAIR',
  'CARPENTER',
  'PAINTER',
  'PEST_CONTROL',
  'CLEANING',
  'HOUSE_HELP',
  'COOK',
  'DRIVER',
  'MOVER',
  'GARDENER',
  'BEAUTICIAN',
  'TUTOR',
  'IT_SUPPORT',
  'OTHER',
] as const;

export type Trade = (typeof TRADES)[number];

export interface ProfessionalProfile {
  userId: string;
  displayName: string | null;
  trade: Trade;
  businessName: string | null;
  about: string | null;
  /** A published business address — exact, unlike a member's fuzzed home point. */
  shopAddress: string | null;
  shopLat: number | null;
  shopLon: number | null;
  serviceRadiusKm: number;
  yearsExperience: number | null;
  licenceRef: string | null;
  insuranceRef: string | null;
  languages: string | null;
  contactPhone: string | null;
  state: 'PENDING' | 'ACTIVE' | 'SUSPENDED';
  idChecked: boolean;
  idCheckedAt: string | null;
  createdAt: string;
}

export interface ReviewItem {
  id: string;
  userId: string;
  displayName: string | null;
  phone: string | null;
  kind: VerificationKind;
  state: VerificationState;
  memberNote: string | null;
  evidenceKeys: string[];
  submittedAt: string;
  /** When the evidence should be destroyed. */
  purgeAfter: string | null;
  professional: ProfessionalProfile | null;
}
