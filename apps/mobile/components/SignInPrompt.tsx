import { Pressable, Text, View } from 'react-native';
import { useAuth } from '@/lib/auth';
import { space } from '@/lib/theme';
import { useTheme } from '@/lib/useTheme';

/**
 * What a guest sees on a screen that only means something with an account.
 *
 * Browsing never needs this; it is for the tabs that are about *you* (your
 * requests, your profile, listing something). Showing it instead of firing the
 * screen's queries keeps a guest from collecting a wall of 401s, and sign-in
 * brings them straight back here.
 */
export function SignInPrompt({ title, body, returnTo }: { title: string; body: string; returnTo: string }) {
  const { s } = useTheme();
  const { requireSignIn } = useAuth();

  return (
    <View style={[s.screen, s.content, s.stack, { paddingTop: space[7] }]}>
      <Text style={s.h2}>{title}</Text>
      <Text style={s.muted}>{body}</Text>
      <Pressable style={s.btn} onPress={() => requireSignIn(returnTo)}>
        <Text style={s.btnText}>Sign in with your phone</Text>
      </Pressable>
    </View>
  );
}
