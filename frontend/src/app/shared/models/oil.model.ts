export interface Oil {
  id: number;
  name: string;
  origin: string;
  benefits: string;
  suitableFor: string;
  isActive: boolean;
  images: Array<{
    id: number;
    imagePath: string;
    sortOrder: number;
    isPrimary: boolean;
  }>;
  createdAt: Date;
  updatedAt: Date;
}
