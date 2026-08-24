import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider } from '@/lib/auth';
import { ThemeProvider, useTheme } from '@/lib/useTheme';

/**
 * Everything below the tabs is browsable without an account. The only gate is
 * on the booking action inside a listing, which pushes /sign-in itself.
 */
function Navigator() {
  const { colors, mode } = useTheme();

  return (
    <>
      <StatusBar style={mode === 'dark' ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.bg },
          headerTitleStyle: { color: colors.ink, fontWeight: '700' },
          headerTintColor: colors.accent,
          contentStyle: { backgroundColor: colors.bg },
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="sign-in" options={{ headerShown: false }} />
        <Stack.Screen name="l/[id]" options={{ title: 'Listing' }} />
        <Stack.Screen name="requests/[id]" options={{ title: 'Request' }} />
        <Stack.Screen name="u/[id]" options={{ title: 'Neighbour' }} />
        <Stack.Screen name="dashboard" options={{ title: 'My listings' }} />
      </Stack>
    </>
  );
}

export default function RootLayout() {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: (count, error) => {
              const status = (error as { status?: number }).status;
              if (status && status < 500) return false;
              return count < 2;
            },
          },
        },
      }),
  );

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={client}>
        <ThemeProvider>
          <AuthProvider>
            <Navigator />
          </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
