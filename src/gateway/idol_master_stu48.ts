export type IdolMasterData = {
  id: number;
  name: string;
  description: string;
  group: string;
  tags: string[];
  /** An https URL of a face photo; the gateway downloads it once into the photos folder. */
  photoUrl?: string;
};

// The members are in the roster file (media/idol-master/members.txt); the sample rows that were here
// are removed (a copy is in scratch/idol-master-backup-20261011).
export const STU48_MASTER_DATA: IdolMasterData[] = [];
