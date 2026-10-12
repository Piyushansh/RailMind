import { create } from 'zustand';
import type { TrainSearchResult } from '@/api/trains';
import type { StationDto } from '@/api/stations';

interface BookingDraftState {
  sourceStation: StationDto | null;
  destinationStation: StationDto | null;
  selectedResult: TrainSearchResult | null;
  selectedClass: string | null;
  lastBookingId: number | null;
  setSearchStations: (source: StationDto, destination: StationDto) => void;
  selectTrain: (result: TrainSearchResult, travelClass: string) => void;
  setLastBookingId: (id: number) => void;
  clear: () => void;
}

export const useBookingDraftStore = create<BookingDraftState>((set) => ({
  sourceStation: null,
  destinationStation: null,
  selectedResult: null,
  selectedClass: null,
  lastBookingId: null,
  setSearchStations: (sourceStation, destinationStation) => set({ sourceStation, destinationStation }),
  selectTrain: (selectedResult, selectedClass) => set({ selectedResult, selectedClass }),
  setLastBookingId: (lastBookingId) => set({ lastBookingId }),
  clear: () => set({ selectedResult: null, selectedClass: null }),
}));
