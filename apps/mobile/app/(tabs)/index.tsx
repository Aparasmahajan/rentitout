import { useInfiniteQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import { ListingCardView } from '@/components/ListingCardView';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { KINDS, KIND_LABEL } from '@/lib/format';
import { space } from '@/lib/theme';
import { useTheme } from '@/lib/useTheme';

export default function FeedScreen() {
  const { s, colors } = useTheme();
  const { me } = useAuth();
  const [kind, setKind] = useState<string | null>(null);

  const radiusKm = me?.searchRadiusKm ?? 5;

  const feed = useInfiniteQuery({
    queryKey: ['feed', kind, radiusKm],
    enabled: Boolean(me),
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => api.listings.feed({ kind: kind ?? undefined, cursor: pageParam, radiusKm }),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });

  const items = feed.data?.pages.flatMap((page) => page.items) ?? [];
  const noLocation = (feed.error as { code?: string } | null)?.code === 'no_location';

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

      {feed.isPending ? (
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
              <Text style={s.h2}>{noLocation ? 'Where are you?' : 'Nothing nearby yet'}</Text>
              <Text style={s.muted}>
                {noLocation
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
