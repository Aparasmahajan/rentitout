import { useMutation } from '@tanstack/react-query';
import { Link } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { ListingCardView } from '@/components/ListingCardView';
import { api } from '@/lib/api';
import { KINDS, KIND_LABEL, distance } from '@/lib/format';
import { useTheme } from '@/lib/useTheme';
import type { SearchResponse } from '@/lib/types';

const EXAMPLES = [
  'I need someone who knows video editing and lives within 5 km',
  'ladder this weekend',
  'who can teach guitar nearby',
];

export default function SearchScreen() {
  const { s, colors } = useTheme();
  const [mode, setMode] = useState<'ask' | 'filter'>('ask');
  const [q, setQ] = useState('');
  const [keyword, setKeyword] = useState('');
  const [kinds, setKinds] = useState<string[]>([]);
  const [result, setResult] = useState<SearchResponse | null>(null);

  const run = useMutation({
    mutationFn: () =>
      api.search.run({
        q: mode === 'ask' ? q : undefined,
        keyword: mode === 'filter' ? keyword || undefined : undefined,
        kinds: kinds.length ? kinds : undefined,
      }),
    onSuccess: setResult,
  });

  return (
    <ScrollView style={s.screen} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
      <View style={s.wrap}>
        <Pressable style={[s.chip, mode === 'ask' && s.chipOn]} onPress={() => setMode('ask')}>
          <Text style={[s.chipText, mode === 'ask' && s.chipTextOn]}>Ask</Text>
        </Pressable>
        <Pressable style={[s.chip, mode === 'filter' && s.chipOn]} onPress={() => setMode('filter')}>
          <Text style={[s.chipText, mode === 'filter' && s.chipTextOn]}>Filter</Text>
        </Pressable>
      </View>

      {mode === 'ask' ? (
        <View style={s.stack}>
          <TextInput
            style={[s.input, { minHeight: 76 }]}
            multiline
            value={q}
            onChangeText={setQ}
            placeholder="I need someone who knows video editing and lives within 5 km"
            placeholderTextColor={colors.muted}
          />
          <View style={s.wrap}>
            {EXAMPLES.map((example) => (
              <Pressable key={example} style={s.chip} onPress={() => setQ(example)}>
                <Text style={s.chipText} numberOfLines={1}>
                  {example.length > 28 ? `${example.slice(0, 28)}…` : example}
                </Text>
              </Pressable>
            ))}
          </View>
        </View>
      ) : (
        <View style={s.stack}>
          <TextInput
            style={s.input}
            value={keyword}
            onChangeText={setKeyword}
            placeholder="Keyword"
            placeholderTextColor={colors.muted}
          />
          <View style={s.wrap}>
            {KINDS.map((k) => (
              <Pressable
                key={k}
                style={[s.chip, kinds.includes(k) && s.chipOn]}
                onPress={() => setKinds((prev) => (prev.includes(k) ? prev.filter((x) => x !== k) : [...prev, k]))}
              >
                <Text style={[s.chipText, kinds.includes(k) && s.chipTextOn]}>{KIND_LABEL[k]}</Text>
              </Pressable>
            ))}
          </View>
        </View>
      )}

      <Pressable style={[s.btn, run.isPending && s.disabled]} onPress={() => run.mutate()} disabled={run.isPending}>
        <Text style={s.btnText}>{run.isPending ? 'Looking…' : 'Search'}</Text>
      </Pressable>

      {run.isError ? (
        <View style={s.error}>
          <Text style={s.errorText}>{(run.error as Error).message}</Text>
        </View>
      ) : null}

      {result ? (
        <View style={s.stack}>
          <Text style={s.tiny}>What we understood ({result.parsed.source})</Text>
          <View style={s.wrap}>
            {result.parsed.tags.map((tag) => (
              <View key={tag} style={[s.chip, s.chipOn]}>
                <Text style={[s.chipText, s.chipTextOn]}>{tag}</Text>
              </View>
            ))}
            {result.parsed.radiusKm ? (
              <View style={s.chip}>
                <Text style={s.chipText}>within {result.parsed.radiusKm} km</Text>
              </View>
            ) : null}
            {result.parsed.day ? (
              <View style={s.chip}>
                <Text style={s.chipText}>{result.parsed.day}</Text>
              </View>
            ) : null}
          </View>
        </View>
      ) : null}

      {result?.members.length ? (
        <View style={s.stack}>
          <Text style={s.h2}>Neighbours</Text>
          {result.members.map((member) => (
            <Link key={member.id} href={`/u/${member.id}`} asChild>
              <Pressable style={[s.card, s.cardBody]}>
                <Text style={s.h3}>{member.displayName}</Text>
                <Text style={s.muted}>
                  {member.areaLabel} · {distance(member.distanceKm)}
                </Text>
                <Text style={s.muted}>{member.tags.slice(0, 4).join(' · ')}</Text>
              </Pressable>
            </Link>
          ))}
        </View>
      ) : null}

      {result ? (
        <View style={s.stack}>
          <Text style={s.h2}>{result.listings.length ? 'Listings' : 'Nothing matched'}</Text>
          {result.listings.map((hit) => (
            <ListingCardView
              key={hit.id}
              listing={{ ...hit, owner: { displayName: hit.ownerName, areaLabel: hit.areaLabel } }}
            />
          ))}
        </View>
      ) : null}
    </ScrollView>
  );
}
