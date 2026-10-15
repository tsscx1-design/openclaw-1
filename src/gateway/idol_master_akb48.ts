export type IdolMasterData = {
  id: number;
  name: string;
  birthday: string;
  bloodType: string;
  height: number;
  agency: string;
  selectionCount: number;
  imagePath: string;
  description: string;
  group: string;
  tags: string[];
  /** An https URL of a face photo; the gateway downloads it once into the photos folder. */
  photoUrl?: string;
};

/**
 * AKB48 Master Data (Current Members - Fully Detailed)
 * Building the next batch of current members.
 */
// The AKB48 members are in the roster file (media/idol-master/members.txt); the sample rows that were here
// repeated them and are removed (a copy is in scratch/idol-master-backup-20261011).
export const AKB48_MASTER_DATA_SEC5: IdolMasterData[] = [];
