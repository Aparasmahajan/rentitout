import { useMutation, useQuery } from '@tanstack/react-query';
import * as Location from 'expo-location';
import { Link } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, Switch, Text, TextInput, View } from 'react-native';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { initials } from '@/lib/format';
import { useTheme } from '@/lib/useTheme';

const RELATIONS = [
  { key: 'has', label: 'I can do' },
  { key: 'teaches', label: 'I can teach' },
  { key: 'needs', label: 'I need' },
  { key: 'enjoys', label: 'I enjoy' },
];

export default function MeScreen() {
  const { s, colors } = useTheme();
  const { me, refresh, signOut } = useAuth();

  const [displayName, setDisplayName] = useState('');
  const [areaLabel, setAreaLabel] = useState('');
  const [bio, setBio] = useState('');
  const [radiusKm, setRadiusKm] = useState(5);
  const [openToRequests, setOpenToRequests] = useState(true);
  const [point, setPoint] = useState<{ lat: number; lon: number } | null>(null);
  const [relation, setRelation] = useState('has');
  const [locating, setLocating] = useState(false);

  useEffect(() => {
    if (!me) return;
    setDisplayName(me.displayName);
    setAreaLabel(me.areaLabel ?? '');
    setBio(me.bio ?? '');
    setRadiusKm(me.searchRadiusKm);
    setOpenToRequests(me.openToRequests);
  }, [me]);

  const vocabulary = useQuery({ queryKey: ['tags'], queryFn: () => api.tags.all() });

  const save = useMutation({
    mutationFn: () =>
      api.me.patch({ displayName, areaLabel, bio, searchRadiusKm: radiusKm, openToRequests, ...(point ?? {}) }),
    onSuccess: () => refresh(),
  });

  const toggleTag = useMutation({
    mutationFn: ({ slug, on }: { slug: string; on: boolean }) =>
      on ? api.me.removeTag(slug, relation) : api.me.addTag(slug, relation),
    onSuccess: () => refresh(),
  });

  async function useMyLocation() {
    setLocating(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') return;
      const position = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Low });
      setPoint({ lat: position.coords.latitude, lon: position.coords.longitude });
    } finally {
      setLocating(false);
    }
  }

  if (!me) return null;

  const mine = new Set(me.tags.filter((t) => t.relation === relation).map((t) => t.slug));

  return (
    <ScrollView style={s.screen} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
      <View style={s.row}>
        <View style={s.avatar}>
          <Text style={{ color: colors.accent, fontWeight: '700' }}>{initials(me.displayName)}</Text>
        </View>
        <View style={s.grow}>
          <Text style={s.h2}>{me.displayName}</Text>
          <Text style={s.muted}>
            {me.areaLabel ?? 'No area set'} · {me.completedCount} completed
            {me.idChecked ? ' · ID checked' : ''}
          </Text>
        </View>
      </View>

      <Text style={s.tiny}>Name</Text>
      <TextInput style={s.input} value={displayName} onChangeText={setDisplayName} />

      <Text style={s.tiny}>Neighbourhood</Text>
      <TextInput style={s.input} value={areaLabel} onChangeText={setAreaLabel} placeholder="Kreuzberg" placeholderTextColor={colors.muted} />

      <Text style={s.tiny}>About you</Text>
      <TextInput style={[s.input, { minHeight: 80 }]} multiline value={bio} onChangeText={setBio} />

      <Text style={s.tiny}>Home point</Text>
      <Text style={s.muted}>
        {point
          ? `${point.lat.toFixed(3)}, ${point.lon.toFixed(3)} — saved rounded to about 100 m`
          : me.lat
            ? `${me.lat.toFixed(3)}, ${me.lon?.toFixed(3)} — rounded to about 100 m`
            : 'Not set — the feed needs this'}
      </Text>
      <Pressable style={[s.btn, s.btnGhost, locating && s.disabled]} onPress={useMyLocation} disabled={locating}>
        <Text style={s.btnTextGhost}>{locating ? 'Locating…' : 'Use my current location'}</Text>
      </Pressable>

      <View style={s.between}>
        <Text style={s.body}>Search radius</Text>
        <View style={s.wrap}>
          {[2, 5, 10, 25].map((km) => (
            <Pressable key={km} style={[s.chip, radiusKm === km && s.chipOn]} onPress={() => setRadiusKm(km)}>
              <Text style={[s.chipText, radiusKm === km && s.chipTextOn]}>{km} km</Text>
            </Pressable>
          ))}
        </View>
      </View>

      <View style={s.between}>
        <Text style={s.body}>Open to requests</Text>
        <Switch value={openToRequests} onValueChange={setOpenToRequests} trackColor={{ true: colors.accent }} />
      </View>

      <Pressable style={[s.btn, save.isPending && s.disabled]} onPress={() => save.mutate()} disabled={save.isPending}>
        <Text style={s.btnText}>{save.isPending ? 'Saving…' : 'Save profile'}</Text>
      </Pressable>

      <Text style={s.h2}>Tags</Text>
      <View style={s.wrap}>
        {RELATIONS.map((r) => (
          <Pressable key={r.key} style={[s.chip, relation === r.key && s.chipOn]} onPress={() => setRelation(r.key)}>
            <Text style={[s.chipText, relation === r.key && s.chipTextOn]}>{r.label}</Text>
          </Pressable>
        ))}
      </View>
      <View style={s.wrap}>
        {(vocabulary.data ?? []).map((tag) => (
          <Pressable
            key={tag.slug}
            style={[s.chip, mine.has(tag.slug) && s.chipOn]}
            onPress={() => toggleTag.mutate({ slug: tag.slug, on: mine.has(tag.slug) })}
          >
            <Text style={[s.chipText, mine.has(tag.slug) && s.chipTextOn]}>{tag.label}</Text>
          </Pressable>
        ))}
      </View>

      <Link href="/dashboard" asChild>
        <Pressable style={[s.btn, s.btnGhost]}>
          <Text style={s.btnTextGhost}>My listings</Text>
        </Pressable>
      </Link>

      <Pressable style={[s.btn, s.btnDanger]} onPress={() => void signOut()}>
        <Text style={s.btnTextDanger}>Sign out</Text>
      </Pressable>
    </ScrollView>
  );
}
