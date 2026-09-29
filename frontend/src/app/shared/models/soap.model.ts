export interface SoapOilRef {
  id: number;
  name: string;
}

export interface Soap {
  id: number;
  name: string;
  shortDescription: string;
  fullDescription: string;
  price: number | null;
  weight: string;
  dimensions: string;
  usageInstructions: string;
  storageInstructions: string;
  ingredients: string;
  features: string;
  oils?: SoapOilRef[];
  essentialOils?: SoapOilRef[];
  oilIds?: number[];
  essentialOilIds?: number[];
  isFeatured: boolean;
  isActive: boolean;
  category: {
    id: number;
    name: string;
  } | null;
  images: Array<{
    id: number;
    imagePath: string;
    sortOrder: number;
    isPrimary: boolean;
  }>;
  createdAt: Date;
  updatedAt: Date;
}
