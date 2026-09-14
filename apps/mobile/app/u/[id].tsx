import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, ScrollView, Text, View } from 'react-native';
import { api } from '@/lib/api';
import { distance, initials } from '@/lib/format';
import { useTheme } from '@/lib/useTheme';

const RELATION_LABEL: Record<string, string> = {
  has: 'Can do',
  teaches: 'Teaches',
  needs: 'Needs',
  enjoys: 'Enjoys',
};

export default function ProfileScreen() {
  const { s, colors } = useTheme();
  const { id } = useLocalSearchParams<{ id: string }>();
  const profile = useQuery({ queryKey: ['profile', id], queryFn: () => api.users.profile(id) });

  if (profile.isPending) return <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />;
  if (profile.isError) {
    return (
      <View style={[s.screen, s.content]}>
        <View style={s.error}>
          <Text style={s.errorText}>{(profile.error as Error).message}</Text>
        </View>
      </View>
    );
  }

  const p = profile.data;
  const groups = Object.keys(RELATION_LABEL)
    .map((relation) => ({ relation, tags: p.tags.filter((t) => t.relation === relation) }))
    .filter((group) => group.tags.length > 0);

  return (
    <ScrollView style={s.screen} contentContainerStyle={s.content}>
      <View style={s.row}>
        <View style={s.avatar}>
          <Text style={{ color: colors.accent, fontWeight: '700' }}>{initials(p.displayName)}</Text>
        </View>
        <View>
          <Text style={s.h2}>{p.displayName}</Text>
          <Text style={s.muted}>
            {p.areaLabel}
            {p.distanceKm !== null ? ` · ${distance(p.distanceKm)}` : ''}
          </Text>
        </View>
      </View>

      <View style={s.wrap}>
        {p.idChecked ? (
          <View style={s.badge}>
            <Text style={s.badgeText}>Verified</Text>
          </View>
        ) : null}
        {p.ratingAvg !== null ? (
          <View style={s.badge}>
            <Text style={s.badgeText}>{p.ratingAvg.toFixed(1)} ★</Text>
          </View>
        ) : null}
        <View style={s.badge}>
          <Text style={s.badgeText}>{p.completedCount} completed</Text>
        </View>
      </View>

      {p.bio ? <Text style={s.body}>{p.bio}</Text> : null}

      {groups.map((group) => (
        <View key={group.relation} style={s.stack}>
          <Text style={s.tiny}>{RELATION_LABEL[group.relation]}</Text>
          <View style={s.wrap}>
            {group.tags.map((tag) => (
              <View key={tag.id} style={s.chip}>
                <Text style={s.chipText}>{tag.label}</Text>
              </View>
            ))}
          </View>
        </View>
      ))}
    </ScrollView>
  );
}
