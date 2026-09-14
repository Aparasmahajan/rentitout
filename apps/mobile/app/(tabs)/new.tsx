import { useMutation, useQuery } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Image, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { ApiError, api, uploadPhoto } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { KINDS, KIND_LABEL, UNITS } from '@/lib/format';
import { useTheme } from '@/lib/useTheme';
import type { ListingKind, Unit } from '@/lib/types';

export default function NewListingScreen() {
  const { s, colors } = useTheme();
  const router = useRouter();
  const { me } = useAuth();

  const [kind, setKind] = useState<ListingKind>('RENT_ITEM');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [unit, setUnit] = useState<Unit>('DAY');
  const [deposit, setDeposit] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [photo, setPhoto] = useState<{ uri: string; mimeType: string } | null>(null);

  const vocabulary = useQuery({ queryKey: ['tags'], queryFn: () => api.tags.all() });

  const toMinor = (value: string) => (value.trim() === '' ? null : Math.round(Number(value) * 100));
  const isNeed = kind === 'OPEN_NEED';
  const isSale = kind === 'SELL_ITEM';

  const publish = useMutation({
    mutationFn: async () => {
      const listing = await api.listings.create({
        kind,
        title: title.trim(),
        description: description.trim() || null,
        priceMinor: isNeed || isSale ? null : toMinor(price),
        unit: isNeed || isSale ? null : unit,
        depositMinor: toMinor(deposit) ?? 0,
        buyPriceMinor: isSale ? toMinor(price) : null,
        lat: me?.lat ?? 52.4996,
        lon: me?.lon ?? 13.418,
        tags,
      });
      if (photo) {
        const key = await uploadPhoto(photo.uri, photo.mimeType);
        await api.listings.attachPhoto(listing.id, key);
      }
      return listing;
    },
    onSuccess: (listing) => {
      setTitle('');
      setDescription('');
      setPrice('');
      setPhoto(null);
      setTags([]);
      router.push(`/l/${listing.id}`);
    },
  });

  async function pickPhoto() {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.7,
    });
    if (!result.canceled && result.assets[0]) {
      const asset = result.assets[0];
      setPhoto({ uri: asset.uri, mimeType: asset.mimeType ?? 'image/jpeg' });
    }
  }

  return (
    <ScrollView style={s.screen} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
      <Text style={s.tiny}>What is it?</Text>
      <View style={s.wrap}>
        {KINDS.map((k) => (
          <Pressable key={k} style={[s.chip, kind === k && s.chipOn]} onPress={() => setKind(k)}>
            <Text style={[s.chipText, kind === k && s.chipTextOn]}>{KIND_LABEL[k]}</Text>
          </Pressable>
        ))}
      </View>

      <TextInput
        style={s.input}
        value={title}
        onChangeText={setTitle}
        placeholder="Aluminium ladder, 3 m"
        placeholderTextColor={colors.muted}
      />

      <TextInput
        style={[s.input, { minHeight: 90 }]}
        multiline
        value={description}
        onChangeText={setDescription}
        placeholder="Light enough to carry up a stairwell."
        placeholderTextColor={colors.muted}
      />

      {!isNeed ? (
        <View style={s.row}>
          <TextInput
            style={[s.input, s.grow]}
            value={price}
            onChangeText={setPrice}
            keyboardType="decimal-pad"
            placeholder={isSale ? 'Sale price ₹' : 'Rate ₹'}
            placeholderTextColor={colors.muted}
          />
          {!isSale ? (
            <View style={s.wrap}>
              {UNITS.slice(0, 3).map((u) => (
                <Pressable key={u} style={[s.chip, unit === u && s.chipOn]} onPress={() => setUnit(u)}>
                  <Text style={[s.chipText, unit === u && s.chipTextOn]}>{u.toLowerCase()}</Text>
                </Pressable>
              ))}
            </View>
          ) : null}
        </View>
      ) : null}

      {!isNeed ? (
        <TextInput
          style={s.input}
          value={deposit}
          onChangeText={setDeposit}
          keyboardType="decimal-pad"
          placeholder="Refundable deposit ₹ (optional)"
          placeholderTextColor={colors.muted}
        />
      ) : null}

      <Pressable style={[s.btn, s.btnGhost]} onPress={pickPhoto}>
        <Text style={s.btnTextGhost}>{photo ? 'Change photo' : 'Add a photo'}</Text>
      </Pressable>
      {photo ? <Image source={{ uri: photo.uri }} style={s.thumb} resizeMode="cover" /> : null}

      <Text style={s.tiny}>Tags</Text>
      <View style={s.wrap}>
        {(vocabulary.data ?? []).slice(0, 30).map((tag) => (
          <Pressable
            key={tag.slug}
            style={[s.chip, tags.includes(tag.slug) && s.chipOn]}
            onPress={() =>
              setTags((prev) => (prev.includes(tag.slug) ? prev.filter((t) => t !== tag.slug) : [...prev, tag.slug]))
            }
          >
            <Text style={[s.chipText, tags.includes(tag.slug) && s.chipTextOn]}>{tag.label}</Text>
          </Pressable>
        ))}
      </View>

      {publish.isError ? (
        <View style={s.error}>
          <Text style={s.errorText}>{(publish.error as ApiError).message}</Text>
        </View>
      ) : null}

      <Pressable
        style={[s.btn, (publish.isPending || title.trim().length < 3) && s.disabled]}
        onPress={() => publish.mutate()}
        disabled={publish.isPending || title.trim().length < 3}
      >
        <Text style={s.btnText}>{publish.isPending ? 'Publishing…' : 'Publish'}</Text>
      </Pressable>
    </ScrollView>
  );
}
