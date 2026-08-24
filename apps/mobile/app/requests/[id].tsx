import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { ApiError, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { day, money, when } from '@/lib/format';
import { space } from '@/lib/theme';
import { useTheme } from '@/lib/useTheme';
import { useThread } from '@/lib/useThread';

type Action = 'accept' | 'decline' | 'start' | 'complete' | 'cancel';

export default function RequestScreen() {
  const { s, colors } = useTheme();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const { messages, live, send } = useThread(id);
  const [draft, setDraft] = useState('');

  const request = useQuery({ queryKey: ['request', id], queryFn: () => api.requests.get(id) });

  const act = useMutation({
    mutationFn: (action: Action) => api.requests[action](id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['request', id] }),
  });

  if (request.isPending) return <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />;
  if (request.isError) {
    return (
      <View style={[s.screen, s.content]}>
        <View style={s.error}>
          <Text style={s.errorText}>{(request.error as Error).message}</Text>
        </View>
      </View>
    );
  }

  const r = request.data;
  const b = r.breakdown;

  return (
    <KeyboardAvoidingView style={s.screen} behavior={Platform.OS === 'ios' ? 'padding' : undefined} keyboardVerticalOffset={90}>
      <ScrollView contentContainerStyle={s.content}>
        <View style={s.between}>
          <Text style={s.h2}>{r.listingTitle}</Text>
          <View style={s.badge}>
            <Text style={s.badgeText}>{r.status.replace('_', ' ')}</Text>
          </View>
        </View>

        <Text style={s.muted}>
          {day(r.startDate)}
          {r.endDate !== r.startDate ? ` → ${day(r.endDate)}` : ''} · {r.units} {r.unit.toLowerCase()}
          {r.units > 1 ? 's' : ''} · {r.iAmOwner ? 'you are lending' : 'you are borrowing'}
        </Text>

        <View style={[s.card, s.cardBody]}>
          <View style={s.between}>
            <Text style={s.body}>
              {money(b.rateMinor, b.currency)} × {b.units}
            </Text>
            <Text style={s.body}>{money(b.amountMinor, b.currency)}</Text>
          </View>
          <View style={s.between}>
            <Text style={s.muted}>Refundable deposit</Text>
            <Text style={s.body}>{money(b.depositMinor, b.currency)}</Text>
          </View>
          <View style={s.between}>
            <Text style={s.muted}>{b.feeLabel}</Text>
            <Text style={s.body}>{money(b.feeMinor, b.currency)}</Text>
          </View>
          <View style={[s.between, { borderTopWidth: 1, borderTopColor: colors.rule, paddingTop: 8 }]}>
            <Text style={s.h3}>Total</Text>
            <Text style={s.price}>{money(b.totalMinor, b.currency)}</Text>
          </View>
          <Text style={s.muted}>{b.settlementNote}</Text>
        </View>

        {act.isError ? (
          <View style={s.error}>
            <Text style={s.errorText}>{(act.error as ApiError).message}</Text>
          </View>
        ) : null}

        <View style={s.row}>
          {r.iAmOwner && r.status === 'SENT' ? (
            <>
              <Pressable style={[s.btn, s.grow]} onPress={() => act.mutate('accept')}>
                <Text style={s.btnText}>Accept</Text>
              </Pressable>
              <Pressable style={[s.btn, s.btnGhost, s.grow]} onPress={() => act.mutate('decline')}>
                <Text style={s.btnTextGhost}>Decline</Text>
              </Pressable>
            </>
          ) : null}
          {r.status === 'ACCEPTED' ? (
            <Pressable style={[s.btn, s.grow]} onPress={() => act.mutate('start')}>
              <Text style={s.btnText}>Handed over</Text>
            </Pressable>
          ) : null}
          {r.status === 'IN_PROGRESS' || r.status === 'ACCEPTED' ? (
            <Pressable style={[s.btn, s.btnGhost, s.grow]} onPress={() => act.mutate('complete')}>
              <Text style={s.btnTextGhost}>Complete</Text>
            </Pressable>
          ) : null}
        </View>

        <View style={s.between}>
          <Text style={s.h3}>Thread</Text>
          <Text style={s.muted}>{live ? 'live' : 'reconnecting…'}</Text>
        </View>

        <View style={{ gap: space[2] }}>
          {r.message ? (
            <View style={[s.bubble, !r.iAmOwner && s.bubbleMine]}>
              <Text style={{ color: !r.iAmOwner ? '#fff' : colors.ink }}>{r.message}</Text>
            </View>
          ) : null}
          {messages.map((message) => {
            const mine = message.senderId === me?.id;
            return (
              <View key={message.id} style={[s.bubble, mine && s.bubbleMine]}>
                <Text style={{ color: mine ? '#fff' : colors.ink }}>{message.body}</Text>
                <Text style={{ fontSize: 10, color: mine ? 'rgba(255,255,255,0.75)' : colors.muted }}>
                  {when(message.sentAt)}
                </Text>
              </View>
            );
          })}
        </View>
      </ScrollView>

      <View style={[s.row, { padding: space[3], borderTopWidth: 1, borderTopColor: colors.rule }]}>
        <TextInput
          style={[s.input, s.grow]}
          value={draft}
          onChangeText={setDraft}
          placeholder="Message your neighbour"
          placeholderTextColor={colors.muted}
        />
        <Pressable
          style={[s.btn, { paddingHorizontal: 18 }, !draft.trim() && s.disabled]}
          disabled={!draft.trim()}
          onPress={() => {
            const body = draft.trim();
            setDraft('');
            void send(body);
          }}
        >
          <Text style={s.btnText}>Send</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}
