import {
  AppstoreOutlined,
  BarcodeOutlined,
  CarOutlined,
  DatabaseOutlined,
  DownOutlined,
  HomeOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  PlusCircleOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { Avatar, Drawer, Dropdown, Grid, Layout, Modal } from "antd";
import { Button, CompactUI, Empty, Input, StatusPill } from "../ui";
import { type ReactNode, useEffect, useState } from "react";
import "./workspace.css";

const workspacePages = {
  home: { label: "Home", Icon: HomeOutlined },
  products: { label: "Products", Icon: BarcodeOutlined },
  catalog: { label: "Catalog", Icon: AppstoreOutlined },
  // "Configurator" = the vehicle/part selection + price waterfall flow
  // (web/src/configurator/VehiclePartSelectionScreen.tsx, migrated from
  // MUI in D-050) — mounted into this shared shell instead of its own
  // standalone page, per frontend-dev.md's "tái dùng ... cho mọi màn".
  configurator: { label: "Configurator", Icon: CarOutlined },
  definitions: { label: "Catalog definitions", Icon: DatabaseOutlined },
} as const;

export type WorkspacePage = keyof typeof workspacePages;

interface AppShellProps {
  page: WorkspacePage;
  pages: WorkspacePage[];
  title?: string;
  primaryAction?: { label: string; href: string };
  accountName: string;
  roleLabel?: string;
  onSignOut: () => void;
  children: ReactNode;
}

export function AppShell({
  page,
  pages,
  title,
  primaryAction,
  accountName,
  roleLabel,
  onSignOut,
  children,
}: AppShellProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [masterDataOpen, setMasterDataOpen] = useState(false);
  const [workspacePickerOpen, setWorkspacePickerOpen] = useState(false);
  const [modal, setModal] = useState<"search" | "profile" | null>(null);
  const [query, setQuery] = useState("");
  const { lg: desktop } = Grid.useBreakpoint();
  const hasCatalog = pages.includes("catalog");
  const masterDataPages = pages.filter(
    (key) => key === "products" || key === "definitions"
  );
  const sidebarMasterDataPages = masterDataPages.filter(
    (key) => key !== "products"
  );
  const mainPages = pages.filter(
    (key) => key === "home" || key === "catalog" || key === "configurator"
  );
  const sidebarAction = sidebarCollapsed
    ? "Expand sidebar"
    : "Collapse sidebar";

  function openSearch() {
    setQuery("");
    setModal("search");
  }

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        openSearch();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const results = pages.filter(
    (key) =>
      key !== "products" &&
      workspacePages[key].label
        .toLowerCase()
        .includes(query.trim().toLowerCase())
  );

  function navigation(collapsed = false) {
    function pageLink(key: WorkspacePage) {
      const { label, Icon } = workspacePages[key];
      return (
        <a
          key={key}
          href={`#${key}`}
          aria-current={key === page ? "page" : undefined}
          aria-label={collapsed ? label : undefined}
          title={collapsed ? label : undefined}
          onClick={() => setMenuOpen(false)}
        >
          <Icon aria-hidden="true" />
          {!collapsed && label}
        </a>
      );
    }

    return (
      <nav className="workspace-navigation" aria-label="Workspace">
        {mainPages.map(pageLink)}
        {sidebarMasterDataPages.length > 0 && (
          <div
            className="workspace-navigation-group"
            role="group"
            aria-label="Master Data"
          >
            {!collapsed && (
              <div className="workspace-navigation-label" aria-hidden="true">
                Master Data
              </div>
            )}
            {sidebarMasterDataPages.map(pageLink)}
          </div>
        )}
      </nav>
    );
  }

  return (
    <CompactUI>
      <Layout className="workspace">
        <a
          href="#workspace-main"
          className="skip-link"
          onClick={(event) => {
            event.preventDefault();
            document.getElementById("workspace-main")?.focus();
          }}
        >
          Skip to content
        </a>

        <Layout.Header className="workspace-topbar">
          <Button
            className="workspace-sider-toggle"
            variant="quiet"
            icon={sidebarCollapsed ? <MenuOutlined /> : <MenuFoldOutlined />}
            aria-label={sidebarAction}
            title={sidebarAction}
            aria-controls="workspace-sidebar"
            aria-expanded={!sidebarCollapsed}
            onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
          />
          <a
            className="brand workspace-logo"
            href="#home"
            aria-label="CPQ home"
          >
            <span className="brand-mark" aria-hidden="true" />
          </a>
          <Dropdown
            trigger={["click"]}
            open={workspacePickerOpen}
            onOpenChange={setWorkspacePickerOpen}
            menu={{
              className: "workspace-toolbar-menu",
              items: [
                { key: "current", label: "CPQ Workspace", disabled: true },
                { type: "divider" },
                { key: "home", label: <a href="#home">Workspace home</a> },
              ],
            }}
          >
            <Button
              className="workspace-picker"
              variant="quiet"
              aria-label="CPQ Workspace"
              aria-haspopup="menu"
              aria-expanded={workspacePickerOpen}
              icon={
                <Avatar shape="square" size={20} aria-hidden="true">
                  C
                </Avatar>
              }
            >
              <span className="workspace-picker-label">CPQ Workspace</span>
              <DownOutlined
                className="workspace-picker-arrow"
                aria-hidden="true"
              />
            </Button>
          </Dropdown>
          <Button
            className="workspace-menu-trigger"
            variant="quiet"
            icon={<MenuOutlined />}
            aria-label="Menu"
            onClick={() => setMenuOpen(true)}
          />
          <div className="workspace-tools">
            <Button
              className="workspace-search"
              variant="quiet"
              icon={<SearchOutlined />}
              aria-label="Search"
              onClick={openSearch}
            >
              <span className="workspace-search-label">Search…</span>
              <kbd>⌘ / Ctrl K</kbd>
            </Button>
            {masterDataPages.length > 0 && (
              <nav
                aria-label="Top navigation"
                className="workspace-master-navigation"
              >
                <Dropdown
                  trigger={["click"]}
                  open={masterDataOpen}
                  onOpenChange={setMasterDataOpen}
                  menu={{
                    className: "workspace-toolbar-menu",
                    selectedKeys: [page],
                    items: masterDataPages.map((key) => ({
                      key,
                      label: (
                        <a href={`#${key}`}>{workspacePages[key].label}</a>
                      ),
                    })),
                  }}
                >
                  <Button
                    className="workspace-master-trigger"
                    variant="quiet"
                    icon={<DatabaseOutlined aria-hidden="true" />}
                    aria-label="Master Data"
                    aria-haspopup="menu"
                    aria-expanded={masterDataOpen}
                  >
                    <span className="workspace-master-label">Master Data</span>
                    <DownOutlined
                      className="workspace-master-arrow"
                      aria-hidden="true"
                    />
                  </Button>
                </Dropdown>
              </nav>
            )}
            {hasCatalog && (
              <Dropdown
                trigger={["click"]}
                menu={{
                  className: "workspace-toolbar-menu",
                  items: [
                    {
                      key: "configure",
                      label: <a href="#catalog">Configure a product</a>,
                    },
                    ...(pages.includes("definitions")
                      ? [
                          {
                            key: "definitions",
                            label: (
                              <a href="#definitions">
                                View catalog definitions
                              </a>
                            ),
                          },
                        ]
                      : []),
                  ],
                }}
              >
                <Button
                  className="workspace-quick-action"
                  variant="quiet"
                  icon={<PlusCircleOutlined aria-hidden="true" />}
                >
                  New
                </Button>
              </Dropdown>
            )}
            <Dropdown
              trigger={["click"]}
              menu={{
                items: [
                  {
                    key: "profile",
                    label: "Profile",
                    onClick: () => setModal("profile"),
                  },
                  { key: "sign-out", label: "Sign out", onClick: onSignOut },
                ],
              }}
            >
              <Button
                className="workspace-account"
                variant="quiet"
                aria-label={`Account: ${accountName}`}
                icon={
                  <Avatar size={24}>
                    {Array.from(accountName)[0]?.toUpperCase()}
                  </Avatar>
                }
              />
            </Dropdown>
          </div>
        </Layout.Header>

        <Layout className="workspace-frame">
          <Layout.Sider
            id="workspace-sidebar"
            width={226}
            collapsedWidth={56}
            collapsed={sidebarCollapsed}
            collapsible
            trigger={null}
            theme="light"
            className="workspace-sider"
          >
            {navigation(sidebarCollapsed)}
          </Layout.Sider>

          <Drawer
            placement="left"
            title="CPQ Workspace"
            open={menuOpen && !desktop}
            onClose={() => setMenuOpen(false)}
            size={280}
          >
            {navigation()}
          </Drawer>

          <Layout className="workspace-body">
            {title && (
              <div className="workspace-title">
                <span className="workspace-title-icon" aria-hidden="true">
                  <AppstoreOutlined />
                </span>
                <h1>{title}</h1>
                {primaryAction && (
                  <Button
                    className="workspace-title-action"
                    variant="primary"
                    href={primaryAction.href}
                  >
                    {primaryAction.label}
                  </Button>
                )}
              </div>
            )}

            <main
              key={page}
              id="workspace-main"
              className="workspace-content"
              tabIndex={-1}
            >
              {children}
            </main>
          </Layout>
        </Layout>

        <Modal
          title="Search workspace"
          open={modal === "search"}
          footer={null}
          destroyOnHidden
          onCancel={() => setModal(null)}
        >
          <Input
            autoFocus
            className="workspace-search-input"
            aria-label="Search pages"
            placeholder="Search pages…"
            prefix={<SearchOutlined />}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <div className="workspace-search-results">
            {results.map((key) => (
              <a
                key={key}
                href={`#${key}`}
                aria-label={`Open ${workspacePages[key].label}`}
                onClick={() => setModal(null)}
              >
                {workspacePages[key].label}
              </a>
            ))}
            {results.length === 0 && (
              <Empty
                description="No matching pages"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            )}
          </div>
        </Modal>
        <Modal
          title="Profile"
          open={modal === "profile"}
          footer={null}
          onCancel={() => setModal(null)}
        >
          <strong>{accountName}</strong>
          {roleLabel && (
            <p>
              <StatusPill tone="brand">{roleLabel}</StatusPill>
            </p>
          )}
        </Modal>
      </Layout>
    </CompactUI>
  );
}
