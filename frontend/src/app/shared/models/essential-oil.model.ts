export interface EssentialOil {
  id: number;
  name: string;
  scentNotes: string;
  benefits: string;
  precautions: string;
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
