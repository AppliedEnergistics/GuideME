import {ZipItem} from "but-unzip";

type ZipContent = {
    [p: string]: ZipItem
};

type PatchouliCategory = {
    name: string;
    description: string;
    icon: string;
    parent?: string;
    flag?: string;
    sortnum?: number;
    secret?: boolean;
}
type PatchouliEntry = {
    name: string;
    category: string;
    icon: string;
    pages: PatchouliEntryPage[];
    advancement?: string;
    flag?: string;
    read_by_default?: boolean;
    sortnum?: number;
    turnin?: string;
    extra_recipe_mappings?: Record<string, number>;
    entry_color?: string;
}

interface PatchouliEntryPage extends Record<string, any> {
    type: string;
}
