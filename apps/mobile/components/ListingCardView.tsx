import { Link } from 'expo-router';
import { Image, Pressable, Text, View } from 'react-native';
import { KIND_LABEL, distance, initials, money, rate } from '@/lib/format';
import { useTheme } from '@/lib/useTheme';
import type { ListingKind } from '@/lib/types';

interface Props {
  listing: {
    id: string;
    kind: ListingKind | string;
    title: string;
    priceMinor: number | null;
    unit: string | null;
    buyPriceMinor: number | null;
    currency: string;
    photoUrl: string | null;
    distanceKm: number;
    status?: string;
    owner?: { displayName: string | null; areaLabel: string | null } | null;
  };
  showStatus?: boolean;
}

export function ListingCardView({ listing, showStatus }: Props) {
  const { s, colors } = useTheme();
  const isSale = listing.kind === 'SELL_ITEM' && listing.buyPriceMinor !== null;

  return (
    <Link href={`/l/${listing.id}`} asChild>
      <Pressable style={s.card}>
        {listing.photoUrl ? (
          <Image source={{ uri: listing.photoUrl }} style={s.thumb} resizeMode="cover" />
        ) : (
          <View style={s.thumbPlaceholder}>
            <Text style={{ fontSize: 26, color: colors.muted, letterSpacing: 2 }}>{initials(listing.title)}</Text>
          </View>
        )}

        <View style={s.cardBody}>
          <View style={s.between}>
            <Text style={s.tiny}>{KIND_LABEL[listing.kind as ListingKind] ?? listing.kind}</Text>
            {showStatus && listing.status ? (
              <View style={s.badge}>
                <Text style={s.badgeText}>{listing.status}</Text>
              </View>
            ) : null}
          </View>

          <Text style={s.h3}>{listing.title}</Text>

          <View style={s.between}>
            <Text style={s.price}>
              {isSale
                ? money(listing.buyPriceMinor, listing.currency)
                : rate(listing.priceMinor, listing.unit, listing.currency)}
            </Text>
            <Text style={s.muted}>{distance(listing.distanceKm)}</Text>
          </View>

          {listing.owner?.displayName ? (
            <Text style={s.muted}>
              {listing.owner.displayName}
              {listing.owner.areaLabel ? ` · ${listing.owner.areaLabel}` : ''}
            </Text>
          ) : null}
        </View>
      </Pressable>
    </Link>
  );
}
