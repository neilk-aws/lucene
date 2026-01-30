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

Apache Lucene एक उच्च-प्रदर्शन, पूर्ण-सुविधा युक्त टेक्स्ट सर्च इंजन लाइब्रेरी है
जो Java में लिखी गई है।

[![Build Status](https://ci-builds.apache.org/job/Lucene/job/Lucene-Artifacts-main/badge/icon?subject=Lucene)](https://ci-builds.apache.org/job/Lucene/job/Lucene-Artifacts-main/)
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://develocity.apache.org/scans?search.buildToolType=gradle&search.rootProjectNames=lucene-root)

## ऑनलाइन दस्तावेज़ीकरण

यह README फ़ाइल केवल बुनियादी सेटअप निर्देश प्रदान करती है। अधिक
व्यापक दस्तावेज़ीकरण के लिए, यहाँ जाएं:

- नवीनतम रिलीज़: <https://lucene.apache.org/core/documentation.html>
- नाइटली: <https://ci-builds.apache.org/job/Lucene/job/Lucene-Artifacts-main/javadoc/>
- नए योगदानकर्ताओं को [योगदान गाइड](./CONTRIBUTING.md) पढ़कर शुरुआत करनी चाहिए
- बिल्ड सिस्टम दस्तावेज़ीकरण: [help/](./help/)
- माइग्रेशन गाइड: [lucene/MIGRATE.md](./lucene/MIGRATE.md)

## बिल्डिंग

### बुनियादी चरण:

1. अपने पैकेज मैनेजर का उपयोग करके JDK 25 इंस्टॉल करें या मैन्युअल रूप से
[OpenJDK](https://jdk.java.net/),
[Adoptium](https://adoptium.net/temurin/releases),
[Azul](https://www.azul.com/downloads/),
[Oracle](https://www.oracle.com/java/technologies/downloads/) या किसी अन्य JDK प्रदाता से डाउनलोड करें।
2. Lucene की git रिपॉजिटरी को क्लोन करें (या स्रोत वितरण डाउनलोड करें)।
3. Gradle लांचर स्क्रिप्ट (`gradlew`) चलाएं।

हम मानते हैं कि आप JDK प्राप्त करना और सेटअप करना जानते हैं - यदि आप नहीं जानते, तो हम सुझाव देते हैं कि https://jdk.java.net/ से शुरुआत करें और इस README पर वापस आने से पहले Java के बारे में अधिक जानें।

## योगदान

बग फिक्स, सुधार और नई सुविधाओं का हमेशा स्वागत है!
कृपया योगदान पर जानकारी के लिए [Lucene में योगदान
गाइड](./CONTRIBUTING.md) की समीक्षा करें।

- अतिरिक्त डेवलपर दस्तावेज़ीकरण: [dev-docs/](./dev-docs/)

## चर्चा और समर्थन

- [उपयोगकर्ता मेलिंग सूची](https://lucene.apache.org/core/discussion.html#java-user-list-java-userluceneapacheorg)
- [डेवलपर्स मेलिंग सूची](https://lucene.apache.org/core/discussion.html#developer-lists)
- IRC: `#lucene` और `#lucene-dev` freenode.net पर
