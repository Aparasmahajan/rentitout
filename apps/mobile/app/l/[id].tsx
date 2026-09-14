import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Image, Modal, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { ApiError, api } from '@/lib/api';
import { KIND_LABEL, distance, initials, money, rate } from '@/lib/format';
import { space, radius } from '@/lib/theme';
import { useTheme } from '@/lib/useTheme';
import type { ListingKind } from '@/lib/types';

export default function ListingScreen() {
  const { s, colors } = useTheme();
  const { id } = useLocalSearchParams<{ id: string }>();
  const [booking, setBooking] = useState(false);

  const listing = useQuery({ queryKey: ['listing', id], queryFn: () => api.listings.detail(id) });

  if (listing.isPending) return <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />;
  if (listing.isError) {
    return (
      <View style={[s.screen, s.content]}>
        <View style={s.error}>
          <Text style={s.errorText}>{(listing.error as Error).message}</Text>
        </View>
      </View>
    );
  }

  const l = listing.data;
  const isSale = l.kind === 'SELL_ITEM' && l.buyPriceMinor !== null;
  const bookable = !l.mine && l.status === 'LIVE' && l.priceMinor !== null && l.kind !== 'OPEN_NEED';

  return (
    <ScrollView style={s.screen} contentContainerStyle={s.content}>
      {l.photos.length > 0 ? (
        <Image source={{ uri: l.photos[0].url }} style={s.thumb} resizeMode="cover" />
      ) : (
        <View style={s.thumbPlaceholder}>
          <Text style={{ fontSize: 30, color: colors.muted }}>{initials(l.title)}</Text>
        </View>
      )}

      <Text style={s.tiny}>{KIND_LABEL[l.kind as ListingKind]}</Text>
      <Text style={s.h1}>{l.title}</Text>

      <View style={s.between}>
        <Text style={s.price}>
          {isSale ? money(l.buyPriceMinor, l.currency) : rate(l.priceMinor, l.unit, l.currency)}
        </Text>
        <Text style={s.muted}>{distance(l.distanceKm)}</Text>
      </View>

      {l.depositMinor > 0 ? <Text style={s.muted}>Refundable deposit {money(l.depositMinor, l.currency)}</Text> : null}
      {l.description ? <Text style={s.body}>{l.description}</Text> : null}

      {l.tags.length > 0 ? (
        <View style={s.wrap}>
          {l.tags.map((tag) => (
            <View key={tag} style={s.chip}>
              <Text style={s.chipText}>{tag}</Text>
            </View>
          ))}
        </View>
      ) : null}

      <Link href={`/u/${l.owner.id}`} asChild>
        <Pressable style={[s.card, s.cardBody, s.row]}>
          <View style={s.avatar}>
            <Text style={{ color: colors.accent, fontWeight: '700' }}>{initials(l.owner.displayName)}</Text>
          </View>
          <View style={s.grow}>
            <Text style={s.h3}>{l.owner.displayName ?? 'A neighbour'}</Text>
            <Text style={s.muted}>{l.owner.areaLabel}</Text>
          </View>
          <Text style={s.muted}>View →</Text>
        </Pressable>
      </Link>

      {bookable ? (
        <Pressable style={s.btn} onPress={() => setBooking(true)}>
          <Text style={s.btnText}>Request this</Text>
        </Pressable>
      ) : (
        <Text style={s.muted}>
          {l.mine ? 'This one is yours.' : l.kind === 'OPEN_NEED' ? 'A neighbour is asking for this.' : 'Not available right now.'}
        </Text>
      )}

      <Modal visible={booking} animationType="slide" transparent onRequestClose={() => setBooking(false)}>
        <BookingSheet listing={l} onClose={() => setBooking(false)} />
      </Modal>
    </ScrollView>
  );
}

/** Every number here comes from the server; the client only picks a day and a count. */
function BookingSheet({
  listing,
  onClose,
}: {
  listing: { id: string; title: string; unit: string | null };
  onClose: () => void;
}) {
  const { s, colors } = useTheme();
  const router = useRouter();
  const tomorrow = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);

  const [startDate, setStartDate] = useState(tomorrow);
  const [units, setUnits] = useState(1);
  const [message, setMessage] = useState('');

  const quote = useQuery({
    queryKey: ['quote', listing.id, startDate, units],
    queryFn: () => api.requests.quote(listing.id, startDate, units),
    retry: false,
  });

  const send = useMutation({
    mutationFn: () => api.requests.create({ listingId: listing.id, startDate, units, message }),
    onSuccess: (request) => {
      onClose();
      router.push(`/requests/${request.id}`);
    },
  });

  return (
    <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)' }}>
      <View
        style={{
          backgroundColor: colors.card,
          borderTopLeftRadius: radius.lg,
          borderTopRightRadius: radius.lg,
          padding: space[4],
          gap: space[3],
        }}
      >
        <View style={s.between}>
          <Text style={s.h2}>{listing.title}</Text>
          <Pressable onPress={onClose} style={s.chip}>
            <Text style={s.chipText}>Close</Text>
          </Pressable>
        </View>

        <Text style={s.tiny}>Day (YYYY-MM-DD)</Text>
        <TextInput style={s.input} value={startDate} onChangeText={setStartDate} placeholder={tomorrow} />

        <View style={s.between}>
          <Text style={s.body}>How many {listing.unit?.toLowerCase() ?? 'unit'}s</Text>
          <View style={s.row}>
            <Pressable style={s.chip} onPress={() => setUnits((u) => Math.max(1, u - 1))}>
              <Text style={s.chipText}>−</Text>
            </Pressable>
            <Text style={s.h3}>{units}</Text>
            <Pressable style={s.chip} onPress={() => setUnits((u) => u + 1)}>
              <Text style={s.chipText}>+</Text>
            </Pressable>
          </View>
        </View>

        <TextInput
          style={[s.input, { minHeight: 70 }]}
          multiline
          value={message}
          onChangeText={setMessage}
          placeholder="Hi — I only need it for the Saturday morning."
          placeholderTextColor={colors.muted}
        />

        {quote.data ? (
          <View style={[s.card, s.cardBody]}>
            <View style={s.between}>
              <Text style={s.body}>
                {money(quote.data.rateMinor, quote.data.currency)} × {quote.data.units}
              </Text>
              <Text style={s.body}>{money(quote.data.amountMinor, quote.data.currency)}</Text>
            </View>
            <View style={s.between}>
              <Text style={s.muted}>Refundable deposit</Text>
              <Text style={s.body}>{money(quote.data.depositMinor, quote.data.currency)}</Text>
            </View>
            <View style={s.between}>
              <Text style={s.muted}>{quote.data.feeLabel}</Text>
              <Text style={s.body}>{money(quote.data.feeMinor, quote.data.currency)}</Text>
            </View>
            <View style={[s.between, { borderTopWidth: 1, borderTopColor: colors.rule, paddingTop: 8 }]}>
              <Text style={s.h3}>Total</Text>
              <Text style={s.price}>{money(quote.data.totalMinor, quote.data.currency)}</Text>
            </View>
            <Text style={s.muted}>{quote.data.settlementNote}</Text>
          </View>
        ) : null}

        {send.isError ? (
          <View style={s.error}>
            <Text style={s.errorText}>{(send.error as ApiError).message}</Text>
          </View>
        ) : null}

        <Pressable style={[s.btn, send.isPending && s.disabled]} onPress={() => send.mutate()} disabled={send.isPending}>
          <Text style={s.btnText}>{send.isPending ? 'Sending…' : 'Send request'}</Text>
        </Pressable>
        <Text style={s.muted}>The owner has 48 hours to answer.</Text>
      </View>
    </View>
  );
}
