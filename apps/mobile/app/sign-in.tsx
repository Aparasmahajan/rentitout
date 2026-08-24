import { useRouter } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { ApiError, api, tokenStore } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useTheme } from '@/lib/useTheme';

export default function SignInScreen() {
  const { s, colors } = useTheme();
  const router = useRouter();
  const { setMe } = useAuth();

  const [phone, setPhone] = useState('+491700000001');
  const [displayName, setDisplayName] = useState('');
  const [code, setCode] = useState('');
  const [sent, setSent] = useState(false);
  const [hint, setHint] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function sendCode() {
    setBusy(true);
    setError(null);
    try {
      const res = await api.auth.startOtp(phone.trim());
      setSent(true);
      setHint(res.devCode);
      if (res.devCode) setCode(res.devCode);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not send a code');
    } finally {
      setBusy(false);
    }
  }

  async function verify() {
    setBusy(true);
    setError(null);
    try {
      const tokens = await api.auth.verify(phone.trim(), code.trim(), displayName.trim() || undefined);
      await tokenStore.set(tokens);
      setMe(tokens.me);
      router.replace('/');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'That did not work');
    } finally {
      setBusy(false);
    }
  }

  return (
    <KeyboardAvoidingView style={s.screen} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={[s.content, { paddingTop: 80 }]}>
        <Text style={[s.h1, { fontSize: 40, letterSpacing: 4, textTransform: 'uppercase' }]}>Radius</Text>
        <Text style={s.muted}>
          A neighbourhood index of people, skills and things. One account: you rent, and you lend.
        </Text>

        {error ? (
          <View style={s.error}>
            <Text style={s.errorText}>{error}</Text>
          </View>
        ) : null}

        <View style={s.stack}>
          <Text style={s.tiny}>Phone number</Text>
          <TextInput
            style={s.input}
            value={phone}
            onChangeText={setPhone}
            keyboardType="phone-pad"
            editable={!sent}
            placeholderTextColor={colors.muted}
          />
        </View>

        {!sent ? (
          <>
            <View style={s.stack}>
              <Text style={s.tiny}>Your name (new members only)</Text>
              <TextInput
                style={s.input}
                value={displayName}
                onChangeText={setDisplayName}
                placeholder="Amara Okafor"
                placeholderTextColor={colors.muted}
              />
            </View>
            <Pressable style={[s.btn, busy && s.disabled]} onPress={sendCode} disabled={busy}>
              <Text style={s.btnText}>{busy ? 'Sending…' : 'Send me a code'}</Text>
            </Pressable>
          </>
        ) : (
          <>
            <View style={s.stack}>
              <Text style={s.tiny}>Six-digit code</Text>
              <TextInput
                style={s.input}
                value={code}
                onChangeText={setCode}
                keyboardType="number-pad"
                maxLength={6}
                textContentType="oneTimeCode"
              />
            </View>
            {hint ? (
              <Text style={s.muted}>
                No SMS provider in the POC — the code is {hint}, and it is also in the user-service log.
              </Text>
            ) : null}
            <Pressable style={[s.btn, (busy || code.length !== 6) && s.disabled]} onPress={verify} disabled={busy || code.length !== 6}>
              <Text style={s.btnText}>{busy ? 'Checking…' : 'Sign in'}</Text>
            </Pressable>
            <Pressable style={[s.btn, s.btnGhost]} onPress={() => setSent(false)}>
              <Text style={s.btnTextGhost}>Use a different number</Text>
            </Pressable>
          </>
        )}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
