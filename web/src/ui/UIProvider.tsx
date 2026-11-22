import { ConfigProvider, type ThemeConfig } from "antd";
import { type ReactNode, useSyncExternalStore } from "react";
import "@fontsource-variable/inter";
import "@fontsource-variable/plus-jakarta-sans";
import "./styles.css";

const theme: ThemeConfig = {
  token: {
    colorPrimary: "#202020",
    colorLink: "#0091ff",
    colorText: "#202020",
    colorTextSecondary: "#646464",
    colorBgLayout: "#ffffff",
    colorBorder: "#d4d4d4",
    colorBorderSecondary: "#e8e8e8",
    controlOutline: "rgba(0, 145, 255, 0.22)",
    fontFamily: "var(--font-body)",
    borderRadius: 9,
    borderRadiusLG: 12,
  },
  components: {
    Button: {
      borderRadius: 5,
      borderRadiusSM: 5,
      borderRadiusLG: 5,
      fontFamily: "var(--font-display)",
      fontWeight: 700,
      defaultBorderColor: "#e8e8e8",
      defaultShadow: "none",
      primaryShadow: "none",
    },
    Checkbox: { borderRadiusSM: 4, controlInteractiveSize: 20 },
    Tag: { borderRadiusSM: 9999 },
    Card: { borderRadiusLG: 12 },
    Input: { borderRadius: 9 },
    InputNumber: { borderRadius: 9 },
    Select: { borderRadius: 9 },
  },
};

const reducedMotionTheme: ThemeConfig = {
  ...theme,
  token: { ...theme.token, motion: false },
};

const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

function subscribeToMotionPreference(onChange: () => void) {
  reducedMotion.addEventListener("change", onChange);
  return () => reducedMotion.removeEventListener("change", onChange);
}

export function UIProvider({ children }: { children: ReactNode }) {
  const prefersReducedMotion = useSyncExternalStore(
    subscribeToMotionPreference,
    () => reducedMotion.matches
  );

  return (
    <ConfigProvider theme={prefersReducedMotion ? reducedMotionTheme : theme}>
      {children}
    </ConfigProvider>
  );
}

const compactTheme: ThemeConfig = {
  token: {
    colorPrimary: "#27776f",
    colorLink: "#27776f",
    colorText: "#263739",
    colorTextSecondary: "#516365",
    colorBorderSecondary: "#e3e9e9",
    controlOutline: "rgba(39, 119, 111, 0.24)",
    fontSize: 12,
    controlHeight: 28,
    borderRadius: 9,
  },
  components: {
    Button: {
      borderRadius: 5,
      borderRadiusSM: 5,
      borderRadiusLG: 5,
      fontWeight: 600,
    },
    Card: { headerHeight: 40, headerPadding: 16, bodyPadding: 16, borderRadiusLG: 12 },
    Table: { cellPaddingBlock: 8, cellPaddingInline: 12 },
  },
};

export function CompactUI({ children }: { children: ReactNode }) {
  return <ConfigProvider theme={compactTheme}>{children}</ConfigProvider>;
}
