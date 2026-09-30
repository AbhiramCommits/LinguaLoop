import { describe, expect, it } from "vitest";
import { bundleToYaml } from "./AuthorScreen";

const bundle = {
  languageCode: "fr",
  languageName: "French",
  unitTitle: "Unité 1 · Salutations",
  unitPosition: 1,
  lessons: [
    {
      key: 1,
      title: "Salutations",
      position: 1,
      exercises: [
        {
          key: 11,
          type: "TRANSLATE" as const,
          prompt: "Good morning",
          answer: "Bonjour",
          choices: "",
          caption: "",
        },
        {
          key: 12,
          type: "MULTIPLE_CHOICE" as const,
          prompt: "Which is hello?",
          answer: "Bonjour",
          choices: "Bonjour\nAu revoir\nMerci",
          caption: "",
        },
        {
          key: 13,
          type: "LISTEN" as const,
          prompt: "Listen and type what you hear.",
          answer: "Bonjour",
          choices: "",
          caption: "Bonjour",
        },
      ],
    },
  ],
};

describe("bundleToYaml", () => {
  it("serializes a bundle to importable YAML", () => {
    const yaml = bundleToYaml(bundle);

    expect(yaml).toContain("code: fr");
    expect(yaml).toContain("name: French");
    expect(yaml).toContain("title: 'Unité 1 · Salutations'");
    expect(yaml).toContain("position: 1");
    expect(yaml).toContain("- type: TRANSLATE");
    expect(yaml).toContain("choices: [Bonjour, Au revoir, Merci]");
    expect(yaml).toContain("caption: Bonjour");
    expect(yaml).toContain("prompt: Listen and type what you hear.");
  });

  it("quotes values that contain YAML-special characters", () => {
    const yaml = bundleToYaml({
      ...bundle,
      unitTitle: "Salutations: niveau 1",
    });
    expect(yaml).toContain("title: 'Salutations: niveau 1'");
  });
});
