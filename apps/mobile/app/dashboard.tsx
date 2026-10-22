import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { ListingCardView } from '@/components/ListingCardView';
import { api } from '@/lib/api';
import { useTheme } from '@/lib/useTheme';

export default function DashboardScreen() {
  const { s, colors } = useTheme();
  const queryClient = useQueryClient();
  const listings = useQuery({ queryKey: ['my-listings'], queryFn: () => api.listings.mine() });

  const toggle = useMutation({
    mutationFn: ({ id, live }: { id: string; live: boolean }) =>
      live ? api.listings.pause(id) : api.listings.resume(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-listings'] }),
  });

  if (listings.isPending) return <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />;

  return (
    <FlatList
      style={s.screen}
      contentContainerStyle={s.content}
      data={listings.data ?? []}
      keyExtractor={(item) => item.id}
      ListEmptyComponent={<Text style={s.muted}>Nothing listed yet.</Text>}
      renderItem={({ item }) => (
        <View style={s.stack}>
          <ListingCardView listing={item} showStatus />
          {/* A removed listing stays removed (listing_unlisted), so offer nothing to press. */}
          {item.status === 'UNLISTED' ? (
            <Text style={s.muted}>Removed</Text>
          ) : (
            <Pressable
              style={[s.btn, s.btnGhost, toggle.isPending && s.disabled]}
              disabled={toggle.isPending}
              onPress={() => toggle.mutate({ id: item.id, live: item.status === 'LIVE' })}
            >
              <Text style={s.btnTextGhost}>{item.status === 'LIVE' ? 'Pause' : 'Resume'}</Text>
            </Pressable>
          )}
        </View>
      )}
    />
  );
}
