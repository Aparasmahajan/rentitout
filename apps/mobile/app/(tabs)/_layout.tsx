import { Tabs } from 'expo-router';
import { Text } from 'react-native';
import { useTheme } from '@/lib/useTheme';

/**
 * Five tabs, text labels, no icon font. Adding an icon set is a design
 * decision; the shape of the app is not waiting on it.
 */
function TabLabel({ label, focused, colors }: { label: string; focused: boolean; colors: { accent: string; muted: string } }) {
  return (
    <Text
      style={{
        fontSize: 11,
        letterSpacing: 0.6,
        textTransform: 'uppercase',
        color: focused ? colors.accent : colors.muted,
        fontWeight: focused ? '700' : '500',
      }}
    >
      {label}
    </Text>
  );
}

export default function TabsLayout() {
  const { colors } = useTheme();
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: colors.bg },
        headerTitleStyle: { color: colors.ink, fontWeight: '700' },
        tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.rule },
        tabBarShowLabel: true,
        tabBarIconStyle: { display: 'none' },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{ title: 'Nearby', tabBarLabel: ({ focused }) => <TabLabel label="Feed" focused={focused} colors={colors} /> }}
      />
      <Tabs.Screen
        name="search"
        options={{ title: 'Search', tabBarLabel: ({ focused }) => <TabLabel label="Search" focused={focused} colors={colors} /> }}
      />
      <Tabs.Screen
        name="new"
        options={{ title: 'List something', tabBarLabel: ({ focused }) => <TabLabel label="List" focused={focused} colors={colors} /> }}
      />
      <Tabs.Screen
        name="requests"
        options={{ title: 'Requests', tabBarLabel: ({ focused }) => <TabLabel label="Requests" focused={focused} colors={colors} /> }}
      />
      <Tabs.Screen
        name="me"
        options={{ title: 'You', tabBarLabel: ({ focused }) => <TabLabel label="Me" focused={focused} colors={colors} /> }}
      />
    </Tabs>
  );
}
