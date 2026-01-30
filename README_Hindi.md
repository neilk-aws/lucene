<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

# Apache Lucene

![Lucene Logo](https://lucene.apache.org/theme/images/lucene/lucene_logo_green_300.png?v=0e493d7a)

Apache Lucene एक उच्च-प्रदर्शन, पूर्ण-विशेषताओं वाली टेक्स्ट सर्च इंजन लाइब्रेरी है
जो Java में लिखी गई है।

[![Build Status](https://ci-builds.apache.org/job/Lucene/job/Lucene-Artifacts-main/badge/icon?subject=Lucene)](https://ci-builds.apache.org/job/Lucene/job/Lucene-Artifacts-main/)
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://develocity.apache.org/scans?search.buildToolType=gradle&search.rootProjectNames=lucene-root)

## ऑनलाइन दस्तावेज़ीकरण

यह README फ़ाइल केवल बुनियादी सेटअप निर्देश शामिल करती है। अधिक
व्यापक दस्तावेज़ीकरण के लिए, यहाँ जाएं:

- नवीनतम रिलीज़: <https://lucene.apache.org/core/documentation.html>
- Nightly: <https://ci-builds.apache.org/job/Lucene/job/Lucene-Artifacts-main/javadoc/>
- नए योगदानकर्ताओं को [योगदान गाइड](./CONTRIBUTING.md) पढ़कर शुरू करना चाहिए
- Build System दस्तावेज़ीकरण: [help/](./help/)
- Migration Guide: [lucene/MIGRATE.md](./lucene/MIGRATE.md)

## निर्माण

### बुनियादी चरण:

1. अपने package manager का उपयोग करके JDK 25 इंस्टॉल करें या मैन्युअल रूप से डाउनलोड करें
[OpenJDK](https://jdk.java.net/),
[Adoptium](https://adoptium.net/temurin/releases),
[Azul](https://www.azul.com/downloads/),
[Oracle](https://www.oracle.com/java/technologies/downloads/) या किसी अन्य JDK प्रदाता से।
2. Lucene की git repository को क्लोन करें (या source distribution डाउनलोड करें)।
3. gradle launcher script (`gradlew`) चलाएं।

हम मान लेते हैं कि आप जानते हैं कि JDK कैसे प्राप्त और सेटअप करें - यदि आप नहीं जानते हैं, तो हम सुझाव देते हैं कि https://jdk.java.net/ से शुरू करें और इस README पर वापस आने से पहले Java के बारे में अधिक जानें।

## योगदान

बग फिक्स, सुधार और नई सुविधाएं हमेशा स्वागत योग्य हैं!
कृपया योगदान के बारे में जानकारी के लिए [Lucene में योगदान
गाइड](./CONTRIBUTING.md) की समीक्षा करें।

- अतिरिक्त Developer दस्तावेज़ीकरण: [dev-docs/](./dev-docs/)

## चर्चा और सहायता

- [Users Mailing List](https://lucene.apache.org/core/discussion.html#java-user-list-java-userluceneapacheorg)
- [Developers Mailing List](https://lucene.apache.org/core/discussion.html#developer-lists)
- IRC: `#lucene` और `#lucene-dev` freenode.net पर
