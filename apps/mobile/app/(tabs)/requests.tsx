import { useQuery } from '@tanstack/react-query';
import { Link } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { api } from '@/lib/api';
import { day, money, when } from '@/lib/format';
import { space } from '@/lib/theme';
import { useTheme } from '@/lib/useTheme';

export default function RequestsScreen() {
  const { s, colors } = useTheme();
  const [direction, setDirection] = useState<'in' | 'out'>('in');

  const requests = useQuery({
    queryKey: ['requests', direction],
    queryFn: () => api.requests.list(direction),
    refetchInterval: 30_000,
  });

  return (
    <View style={s.screen}>
      <View style={[s.wrap, { padding: space[3] }]}>
        <Pressable style={[s.chip, direction === 'in' && s.chipOn]} onPress={() => setDirection('in')}>
          <Text style={[s.chipText, direction === 'in' && s.chipTextOn]}>Incoming</Text>
        </Pressable>
        <Pressable style={[s.chip, direction === 'out' && s.chipOn]} onPress={() => setDirection('out')}>
          <Text style={[s.chipText, direction === 'out' && s.chipTextOn]}>Outgoing</Text>
        </Pressable>
      </View>

      {requests.isPending ? (
        <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={requests.data ?? []}
          keyExtractor={(item) => item.id}
          contentContainerStyle={s.content}
          onRefresh={() => requests.refetch()}
          refreshing={requests.isRefetching}
          ListEmptyComponent={
            <Text style={s.muted}>
              {direction === 'in' ? 'Nobody has asked for anything of yours yet.' : 'You have not asked for anything yet.'}
            </Text>
          }
          renderItem={({ item }) => (
            <Link href={`/requests/${item.id}`} asChild>
              <Pressable style={[s.card, s.cardBody]}>
                <View style={s.between}>
                  <Text style={s.h3}>{item.listingTitle}</Text>
                  <View style={s.badge}>
                    <Text style={s.badgeText}>{item.status.replace('_', ' ')}</Text>
                  </View>
                </View>
                <View style={s.between}>
                  <Text style={s.muted}>
                    {day(item.startDate)} · {item.units} {item.unit.toLowerCase()}
                    {item.units > 1 ? 's' : ''}
                  </Text>
                  <Text style={s.price}>{money(item.breakdown.totalMinor, item.breakdown.currency)}</Text>
                </View>
                <Text style={s.muted}>
                  {when(item.createdAt)}
                  {item.unreadCount > 0 ? ` · ${item.unreadCount} new` : ''}
                </Text>
              </Pressable>
            </Link>
          )}
        />
      )}
    </View>
  );
}
