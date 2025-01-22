import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'GuideME Documentation',
  tagline: 'Cool Guides',
  favicon: 'img/favicon.ico',

  // Set the production url of your site here
  url: 'https://guideme.appliedenergistics.org',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/',

  // GitHub pages deployment config.
  organizationName: 'AppliedEnergistics',
  projectName: 'GuideME',

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/AppliedEnergistics/GuideME/tree/main/docs/',
          routeBasePath: '/', // Serve the docs at the site's root
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    // Replace with your project's social card
    image: 'img/docusaurus-social-card.jpg',
    navbar: {
      title: 'GuideME',
      logo: {
        alt: 'GuideME Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Documentation',
        },
        {
          href: '/javadoc/',
          label: 'Javadoc',
          position: 'left',
        },
        {
          href: 'https://github.com/AppliedEnergistics/GuideME',
          label: 'GitHub',
          position: 'left',
        },
        {
          href: 'https://curseforge.com/minecraft/mc-mods/guideme',
          label: 'CurseForge',
          position: 'left',
        },
        {
          href: 'https://modrinth.com/mod/guideme',
          label: 'Modrinth',
          position: 'left',
        },
        {
          href: 'https://github.com/AppliedEnergistics/GuideME',
          label: 'Maven',
          position: 'left',
        },
        {
          href: 'https://discord.gg/Zd6t9ka7ne',
          label: 'Discord',
          position: 'left',
        },
      ],
    },
    footer: {
      style: 'dark',
      copyright: `Copyright © ${new Date().getFullYear()} Team AppliedEnergistics. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
