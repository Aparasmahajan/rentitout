import { useInfiniteQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import { ListingCardView } from '@/components/ListingCardView';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { KINDS, KIND_LABEL } from '@/lib/format';
import { space } from '@/lib/theme';
import { useTheme } from '@/lib/useTheme';
import { useViewerPoint } from '@/lib/useViewerPoint';

/**
 * The nearby feed, readable without an account.
 *
 * A guest has no saved point and the API will not guess one, so the feed always
 * sends the viewer point: the member's home, a location the guest shared, or the
 * launch area. It waits only for auth to settle, so a member's first request is
 * not a wasted guest request at the wrong centre.
 */
export default function FeedScreen() {
  const { s, colors } = useTheme();
  const { me, loading } = useAuth();
  const { point, locate, locating } = useViewerPoint();
  const [kind, setKind] = useState<string | null>(null);

  const radiusKm = me?.searchRadiusKm ?? 5;

  const feed = useInfiniteQuery({
    queryKey: ['feed', kind, radiusKm, point.lat, point.lon],
    enabled: !loading,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      api.listings.feed({ kind: kind ?? undefined, cursor: pageParam, radiusKm, lat: point.lat, lon: point.lon }),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });

  const items = feed.data?.pages.flatMap((page) => page.items) ?? [];
  const noLocation = (feed.error as { code?: string } | null)?.code === 'no_location';
  // A member's point comes from their profile; only a guest can borrow the device's.
  const canLocate = !me || me.lat == null;

  return (
    <View style={s.screen}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ padding: space[3], gap: space[2] }}
      >
        <Pressable style={[s.chip, kind === null && s.chipOn]} onPress={() => setKind(null)}>
          <Text style={[s.chipText, kind === null && s.chipTextOn]}>Everything</Text>
        </Pressable>
        {KINDS.map((k) => (
          <Pressable key={k} style={[s.chip, kind === k && s.chipOn]} onPress={() => setKind(k)}>
            <Text style={[s.chipText, kind === k && s.chipTextOn]}>{KIND_LABEL[k]}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <View style={[s.row, s.between, { paddingHorizontal: space[3] }]}>
        <Text style={s.muted}>
          Within {radiusKm} km of {point.label}
        </Text>
        {canLocate ? (
          <Pressable style={s.chip} onPress={() => void locate()} disabled={locating}>
            <Text style={s.chipText}>{locating ? 'Locating…' : 'Use my location'}</Text>
          </Pressable>
        ) : null}
      </View>

      {loading || feed.isPending ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={colors.accent} />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.id}
          contentContainerStyle={s.content}
          refreshControl={<RefreshControl refreshing={feed.isRefetching} onRefresh={() => feed.refetch()} />}
          renderItem={({ item }) => <ListingCardView listing={item} />}
          onEndReachedThreshold={0.4}
          onEndReached={() => feed.hasNextPage && feed.fetchNextPage()}
          ListEmptyComponent={
            <View style={[s.stack, { paddingTop: space[7] }]}>
              <Text style={s.h2}>
                {feed.isError && !noLocation ? 'Could not load the feed' : noLocation ? 'Where are you?' : 'Nothing nearby yet'}
              </Text>
              <Text style={s.muted}>
                {feed.isError && !noLocation
                  ? 'Pull down to try again.'
                  : noLocation
                    ? 'Set your area on the Me tab so the feed knows what “nearby” means.'
                    : `Nothing inside ${radiusKm} km. Widen your radius on the Me tab, or list the first thing.`}
              </Text>
            </View>
          }
          ListFooterComponent={
            feed.isFetchingNextPage ? <ActivityIndicator color={colors.accent} style={{ marginVertical: 20 }} /> : null
          }
        />
      )}
    </View>
  );
}
