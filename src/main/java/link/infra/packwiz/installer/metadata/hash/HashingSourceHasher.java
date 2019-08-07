package link.infra.packwiz.installer.metadata.hash;

import okio.HashingSource;
import okio.Source;

public class HashingSourceHasher implements IHasher {
	String type;

	public HashingSourceHasher(String type) {
		this.type = type;
	}

	// i love naming things
	private class HashingSourceGeneralHashingSource extends GeneralHashingSource {
		HashingSource delegateHashing;
		HashingSourceHash value;

		public HashingSourceGeneralHashingSource(HashingSource delegate) {
			super(delegate);
			delegateHashing = delegate;
		}

		@Override
		public Hash getHash() {
			if (value == null) {
				value = new HashingSourceHash(delegateHashing.hash().hex());
			}
			return value;
		}

	}

	// this some funky inner class stuff
	// each of these classes is specific to the instance of the HasherHashingSource
	// therefore HashingSourceHashes from different parent instances will be not instanceof each other
	private class HashingSourceHash extends Hash {
		String value;
		private HashingSourceHash(String value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof HashingSourceHash)) {
				return false;
			}
			HashingSourceHash objHash = (HashingSourceHash) obj;
			if (value != null) {
				return value.equals(objHash.value);
			} else {
				return objHash.value == null;
			}
		}

		@Override
		public String toString() {
			return type + ": " + value;
		}

		@Override
		protected String getStringValue() {
			return value;
		}

		@Override
		protected String getType() {
			return type;
		}
	}

	@Override
	public GeneralHashingSource getHashingSource(Source delegate) {
		switch (type) {
			case "sha256":
			return new HashingSourceGeneralHashingSource(HashingSource.sha256(delegate));
			// TODO: support other hash types
		}
		throw new RuntimeException("Invalid hash type provided");
	}

	@Override
	public Hash getHash(String value) {
		return new HashingSourceHash(value);
	}
	
}